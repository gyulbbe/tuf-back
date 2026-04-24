package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveCurrentTurnResponseDto;
import io.github.gyulbbe.draft.dto.DraftLivePermissionsResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveRosterItemResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveSessionInfoResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveSnapshotResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveTeamResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionSummaryResponseDto;
import io.github.gyulbbe.draft.dto.DraftTeamResponseDto;
import io.github.gyulbbe.draft.repository.DraftQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
public class DraftSnapshotService {

    private final DraftQueryRepository draftQueryRepository;
    private final DraftPermissionService draftPermissionService;

    public DraftLiveSnapshotResponseDto getSnapshot(Long sessionId, AuthActor actor) {
        DraftSessionSummaryResponseDto sessionSummary = draftQueryRepository.findSessionSummary(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Draft session could not be found."));

        List<DraftTeamResponseDto> teamDtos = draftQueryRepository.findTeamsBySessionId(sessionId);
        List<DraftCandidateResponseDto> candidates = draftQueryRepository.findCandidatesBySessionId(sessionId);
        List<DraftOrderResponseDto> orders = draftQueryRepository.findOrdersBySessionId(sessionId);
        List<DraftPickResponseDto> picks = draftQueryRepository.findPicksBySessionId(sessionId);
        LocalDateTime serverNow = LocalDateTime.now();

        Map<Long, DraftLiveTeamResponseDto> teamMap = buildTeams(teamDtos);
        attachRoster(teamMap, picks, sessionSummary.getTeamCount());
        DraftLiveCurrentTurnResponseDto currentTurn = buildCurrentTurn(sessionSummary, orders, teamMap, serverNow);

        DraftLiveSnapshotResponseDto snapshot = new DraftLiveSnapshotResponseDto();
        snapshot.setSession(buildSessionInfo(sessionSummary, serverNow));
        snapshot.setCurrentTurn(currentTurn);
        snapshot.setTeams(new ArrayList<>(teamMap.values()));
        snapshot.setAvailableCandidates(filterCandidatesByStatus(candidates, "WAITING"));
        snapshot.setPickedCandidates(filterCandidatesByStatus(candidates, "PICKED"));
        snapshot.setRecentPicks(buildRecentPicks(picks));
        snapshot.setPermissions(buildPermissions(sessionSummary, actor, currentTurn != null ? currentTurn.getTeamId() : null, snapshot.getTeams()));
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto getBroadcastSnapshot(Long sessionId) {
        DraftLiveSnapshotResponseDto snapshot = getSnapshot(sessionId, null);
        snapshot.setPermissions(null);
        return snapshot;
    }

    public DraftLivePermissionsResponseDto getPermissions(Long sessionId, AuthActor actor) {
        return getSnapshot(sessionId, actor).getPermissions();
    }

    private Map<Long, DraftLiveTeamResponseDto> buildTeams(List<DraftTeamResponseDto> teams) {
        return teams.stream()
                .map(team -> {
                    DraftLiveTeamResponseDto responseDto = new DraftLiveTeamResponseDto();
                    responseDto.setId(team.getId());
                    responseDto.setDraftSessionId(team.getDraftSessionId());
                    responseDto.setTeamName(team.getTeamName());
                    responseDto.setDisplayOrder(team.getDisplayOrder());
                    responseDto.setPickerUserId(team.getPickerUserId());
                    responseDto.setPickerUserLoginId(team.getPickerUserLoginId());
                    responseDto.setPickerName(team.getPickerName());
                    return responseDto;
                })
                .collect(Collectors.toMap(
                        DraftLiveTeamResponseDto::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private void attachRoster(
            Map<Long, DraftLiveTeamResponseDto> teamMap,
            List<DraftPickResponseDto> picks,
            Integer teamCount
    ) {
        for (DraftPickResponseDto pick : picks) {
            DraftLiveTeamResponseDto team = teamMap.get(pick.getDraftTeamId());
            if (team == null) {
                continue;
            }

            DraftLiveRosterItemResponseDto rosterItem = new DraftLiveRosterItemResponseDto();
            rosterItem.setPickNo(pick.getPickNo());
            rosterItem.setRoundNo(calculateRoundNo(pick.getPickNo(), teamCount));
            rosterItem.setCandidateUserId(pick.getCandidateUserId());
            rosterItem.setCandidateUserLoginId(pick.getCandidateUserLoginId());
            rosterItem.setCandidateName(pick.getCandidateName());
            rosterItem.setTier(pick.getTier());
            rosterItem.setRace(pick.getRace());
            rosterItem.setPickedByUserId(pick.getPickedByUserId());
            rosterItem.setPickedByUserLoginId(pick.getPickedByUserLoginId());
            rosterItem.setPickedByUserName(pick.getPickedByUserName());
            rosterItem.setPickedAt(pick.getPickedAt());
            team.getRoster().add(rosterItem);
        }
    }

    private Long calculateRoundNo(Long pickNo, Integer teamCount) {
        if (pickNo == null || teamCount == null || teamCount <= 0) {
            return null;
        }
        return ((pickNo - 1) / teamCount) + 1;
    }

    private DraftLiveSessionInfoResponseDto buildSessionInfo(DraftSessionSummaryResponseDto sessionSummary, LocalDateTime serverNow) {
        DraftLiveSessionInfoResponseDto responseDto = new DraftLiveSessionInfoResponseDto();
        responseDto.setId(sessionSummary.getId());
        responseDto.setTitle(sessionSummary.getTitle());
        responseDto.setOwnerUserId(sessionSummary.getOwnerUserId());
        responseDto.setOwnerUserLoginId(sessionSummary.getOwnerUserLoginId());
        responseDto.setOwnerName(sessionSummary.getOwnerName());
        responseDto.setStatus(sessionSummary.getStatus());
        responseDto.setTeamCount(sessionSummary.getTeamCount());
        responseDto.setPickTimeSeconds(sessionSummary.getPickTimeSeconds());
        responseDto.setCurrentPickNo(sessionSummary.getCurrentPickNo());
        responseDto.setCurrentDraftTeamId(sessionSummary.getCurrentDraftTeamId());
        responseDto.setDeadlineAt(sessionSummary.getDeadlineAt());
        responseDto.setStartedAt(sessionSummary.getStartedAt());
        responseDto.setEndedAt(sessionSummary.getEndedAt());
        responseDto.setServerNow(serverNow);
        return responseDto;
    }

    private DraftLiveCurrentTurnResponseDto buildCurrentTurn(
            DraftSessionSummaryResponseDto sessionSummary,
            List<DraftOrderResponseDto> orders,
            Map<Long, DraftLiveTeamResponseDto> teamMap,
            LocalDateTime serverNow
    ) {
        if (sessionSummary.getCurrentPickNo() == null) {
            return null;
        }

        DraftOrderResponseDto currentOrder = orders.stream()
                .filter(order -> Objects.equals(order.getPickNo(), sessionSummary.getCurrentPickNo().longValue()))
                .findFirst()
                .orElse(null);

        if (currentOrder == null) {
            return null;
        }

        DraftLiveCurrentTurnResponseDto responseDto = new DraftLiveCurrentTurnResponseDto();
        responseDto.setPickNo(currentOrder.getPickNo());
        responseDto.setTeamId(currentOrder.getDraftTeamId());

        DraftLiveTeamResponseDto team = teamMap.get(currentOrder.getDraftTeamId());
        responseDto.setTeamName(team != null ? team.getTeamName() : currentOrder.getDraftTeamName());
        responseDto.setRemainingSeconds(calculateRemainingSeconds(sessionSummary.getDeadlineAt(), serverNow));
        return responseDto;
    }

    private Long calculateRemainingSeconds(LocalDateTime deadlineAt, LocalDateTime serverNow) {
        if (deadlineAt == null) {
            return 0L;
        }

        long seconds = Duration.between(serverNow, deadlineAt).getSeconds();
        return Math.max(seconds, 0L);
    }

    private List<DraftCandidateResponseDto> filterCandidatesByStatus(List<DraftCandidateResponseDto> candidates, String status) {
        return candidates.stream()
                .filter(candidate -> status.equals(candidate.getStatus()))
                .toList();
    }

    private List<DraftPickResponseDto> buildRecentPicks(List<DraftPickResponseDto> picks) {
        List<DraftPickResponseDto> recentPicks = new ArrayList<>(picks);
        recentPicks.sort(Comparator.comparing(DraftPickResponseDto::getPickNo).reversed());
        return recentPicks;
    }

    private DraftLivePermissionsResponseDto buildPermissions(
            DraftSessionSummaryResponseDto sessionSummary,
            AuthActor actor,
            Long currentTurnTeamId,
            List<DraftLiveTeamResponseDto> teams
    ) {
        DraftLivePermissionsResponseDto permissions = new DraftLivePermissionsResponseDto();
        permissions.setCanControl(
                draftPermissionService.isAdmin(actor)
                        || (actor != null
                        && actor.userPk() != null
                        && Objects.equals(sessionSummary.getOwnerUserId(), actor.userPk()))
        );

        if (actor == null || actor.userPk() == null || teams.isEmpty()) {
            permissions.setCanPick(false);
            return permissions;
        }

        DraftLiveTeamResponseDto selectedTeam = teams.stream()
                .filter(team -> Objects.equals(team.getPickerUserId(), actor.userPk()))
                .findFirst()
                .orElse(null);

        if (selectedTeam == null) {
            permissions.setCanPick(false);
            return permissions;
        }

        permissions.setMyTeamId(selectedTeam.getId());
        permissions.setMyRole("PICKER");
        permissions.setCanPick(
                currentTurnTeamId != null
                        && Objects.equals(selectedTeam.getId(), currentTurnTeamId)
                        && draftPermissionService.canPickForTeam(currentTurnTeamId, actor.userPk())
        );
        return permissions;
    }
}
