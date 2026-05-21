package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLivePermissionsResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveRosterItemResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveRpsStateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSessionInfoResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSnapshotResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveTeamResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftPickResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionQueryDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftTeamResponseDto;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RpsDraftSnapshotService {

    private final RpsDraftQueryRepository rpsDraftQueryRepository;

    public RpsDraftLiveSnapshotResponseDto getSnapshot(Long sessionId, RpsDraftActor actor) {
        RpsDraftSessionQueryDto session = rpsDraftQueryRepository.findSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("RPS draft session could not be found."));

        List<RpsDraftTeamResponseDto> teams = rpsDraftQueryRepository.findTeamsBySessionId(sessionId);
        List<RpsDraftCandidateResponseDto> candidates = rpsDraftQueryRepository.findCandidatesBySessionId(sessionId);
        List<RpsDraftPickResponseDto> picks = rpsDraftQueryRepository.findPicksBySessionId(sessionId);
        LocalDateTime now = LocalDateTime.now();

        Map<Long, RpsDraftLiveTeamResponseDto> teamMap = buildTeams(teams);
        attachRoster(teamMap, picks, teams.size());

        RpsDraftLiveSnapshotResponseDto snapshot = new RpsDraftLiveSnapshotResponseDto();
        snapshot.setSession(buildSessionInfo(session, now));
        snapshot.setRps(buildRpsState(session));
        snapshot.setTeams(new ArrayList<>(teamMap.values()));
        snapshot.setAvailableCandidates(filterCandidates(candidates, RpsDraftCandidateEntity.STATUS_WAITING));
        snapshot.setPickedCandidates(filterCandidates(candidates, RpsDraftCandidateEntity.STATUS_PICKED));
        snapshot.setRecentPicks(buildRecentPicks(picks));
        snapshot.setPermissions(buildPermissions(session, actor, teams));
        return snapshot;
    }

    public RpsDraftLiveSnapshotResponseDto getBroadcastSnapshot(Long sessionId) {
        RpsDraftLiveSnapshotResponseDto snapshot = getSnapshot(sessionId, null);
        snapshot.setPermissions(null);
        return snapshot;
    }

    private Map<Long, RpsDraftLiveTeamResponseDto> buildTeams(List<RpsDraftTeamResponseDto> teams) {
        return teams.stream()
                .map(team -> {
                    RpsDraftLiveTeamResponseDto dto = new RpsDraftLiveTeamResponseDto();
                    dto.setId(team.getId());
                    dto.setRpsDraftSessionId(team.getRpsDraftSessionId());
                    dto.setTeamName(team.getTeamName());
                    dto.setDisplayOrder(team.getDisplayOrder());
                    dto.setPickerUserId(team.getPickerUserId());
                    dto.setPickerUserLoginId(team.getPickerUserLoginId());
                    dto.setPickerName(team.getPickerName());
                    return dto;
                })
                .collect(Collectors.toMap(
                        RpsDraftLiveTeamResponseDto::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private void attachRoster(
            Map<Long, RpsDraftLiveTeamResponseDto> teamMap,
            List<RpsDraftPickResponseDto> picks,
            int teamCount
    ) {
        for (RpsDraftPickResponseDto pick : picks) {
            RpsDraftLiveTeamResponseDto team = teamMap.get(pick.getRpsDraftTeamId());
            if (team == null) {
                continue;
            }

            RpsDraftLiveRosterItemResponseDto rosterItem = new RpsDraftLiveRosterItemResponseDto();
            rosterItem.setPickNo(pick.getPickNo());
            rosterItem.setRoundNo(calculateRoundNo(pick.getPickNo(), teamCount));
            rosterItem.setCandidateId(pick.getCandidateId());
            rosterItem.setCandidateName(pick.getCandidateName());
            rosterItem.setPickedByUserId(pick.getPickedByUserId());
            rosterItem.setPickedByUserLoginId(pick.getPickedByUserLoginId());
            rosterItem.setPickedByUserName(pick.getPickedByUserName());
            rosterItem.setPickedAt(pick.getPickedAt());
            team.getRoster().add(rosterItem);
        }
    }

    private Long calculateRoundNo(Long pickNo, int teamCount) {
        if (pickNo == null || teamCount <= 0) {
            return null;
        }
        return ((pickNo - 1) / teamCount) + 1;
    }

    private RpsDraftLiveSessionInfoResponseDto buildSessionInfo(RpsDraftSessionQueryDto session, LocalDateTime now) {
        RpsDraftLiveSessionInfoResponseDto info = new RpsDraftLiveSessionInfoResponseDto();
        info.setId(session.getId());
        info.setTitle(session.getTitle());
        info.setOwnerUserId(session.getOwnerUserId());
        info.setOwnerUserLoginId(session.getOwnerUserLoginId());
        info.setOwnerName(session.getOwnerName());
        info.setStatus(session.getStatus());
        info.setCurrentPickNo(session.getCurrentPickNo());
        info.setCurrentDraftTeamId(session.getCurrentDraftTeamId());
        info.setPendingDraftTeamId(session.getPendingDraftTeamId());
        info.setStartedAt(session.getStartedAt());
        info.setEndedAt(session.getEndedAt());
        info.setRegDate(session.getRegDate());
        info.setUpdateDate(session.getUpdateDate());
        info.setServerNow(now);
        return info;
    }

    private RpsDraftLiveRpsStateResponseDto buildRpsState(RpsDraftSessionQueryDto session) {
        boolean team1Submitted = session.getTeam1RpsChoice() != null;
        boolean team2Submitted = session.getTeam2RpsChoice() != null;
        boolean revealChoices = team1Submitted
                && team2Submitted
                && (RpsDraftSessionEntity.STATUS_PICKING.equals(session.getStatus())
                || RpsDraftSessionEntity.STATUS_FINISHED.equals(session.getStatus()));

        RpsDraftLiveRpsStateResponseDto state = new RpsDraftLiveRpsStateResponseDto();
        state.setTeam1Submitted(team1Submitted);
        state.setTeam2Submitted(team2Submitted);
        state.setTeam1Choice(revealChoices ? session.getTeam1RpsChoice() : null);
        state.setTeam2Choice(revealChoices ? session.getTeam2RpsChoice() : null);
        state.setResult(revealChoices ? session.getRpsResult() : RpsDraftSessionEntity.RPS_RESULT_PENDING);
        return state;
    }

    private List<RpsDraftCandidateResponseDto> filterCandidates(List<RpsDraftCandidateResponseDto> candidates, String status) {
        return candidates.stream()
                .filter(candidate -> status.equals(candidate.getStatus()))
                .toList();
    }

    private List<RpsDraftPickResponseDto> buildRecentPicks(List<RpsDraftPickResponseDto> picks) {
        List<RpsDraftPickResponseDto> recentPicks = new ArrayList<>(picks);
        recentPicks.sort(Comparator.comparing(RpsDraftPickResponseDto::getPickNo).reversed());
        return recentPicks;
    }

    private RpsDraftLivePermissionsResponseDto buildPermissions(
            RpsDraftSessionQueryDto session,
            RpsDraftActor actor,
            List<RpsDraftTeamResponseDto> teams
    ) {
        RpsDraftLivePermissionsResponseDto permissions = new RpsDraftLivePermissionsResponseDto();
        permissions.setMyRole("VIEWER");

        if (actor == null || actor.userPk() == null) {
            return permissions;
        }

        boolean isOwner = Objects.equals(session.getOwnerUserId(), actor.userPk());
        RpsDraftTeamResponseDto myTeam = teams.stream()
                .filter(team -> Objects.equals(team.getPickerUserId(), actor.userPk()))
                .findFirst()
                .orElse(null);

        permissions.setCanControl(isOwner);
        permissions.setMyTeamId(myTeam != null ? myTeam.getId() : null);

        if (isOwner && myTeam != null) {
            permissions.setMyRole("OWNER_PICKER");
        } else if (isOwner) {
            permissions.setMyRole("OWNER");
        } else if (myTeam != null) {
            permissions.setMyRole("PICKER");
        }

        if (myTeam == null) {
            return permissions;
        }

        boolean myChoiceSubmitted = myTeam.getDisplayOrder() == 1
                ? session.getTeam1RpsChoice() != null
                : session.getTeam2RpsChoice() != null;

        permissions.setCanSubmitRps(
                RpsDraftSessionEntity.STATUS_RPS_PENDING.equals(session.getStatus()) && !myChoiceSubmitted
        );
        permissions.setCanPick(
                RpsDraftSessionEntity.STATUS_PICKING.equals(session.getStatus())
                        && Objects.equals(session.getCurrentDraftTeamId(), myTeam.getId())
        );
        return permissions;
    }
}
