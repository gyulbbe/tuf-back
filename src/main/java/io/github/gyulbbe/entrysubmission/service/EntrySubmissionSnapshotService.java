package io.github.gyulbbe.entrysubmission.service;

import io.github.gyulbbe.entrysubmission.auth.EntrySubmissionActor;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionEntryResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionMatchResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionPermissionsResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionPlayerResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSessionInfoResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSnapshotResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionTeamResponseDto;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionEntryEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionPlayerEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionSessionEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionTeamEntity;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionEntryRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionPlayerRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionSessionRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EntrySubmissionSnapshotService {

    private final EntrySubmissionSessionRepository entrySubmissionSessionRepository;
    private final EntrySubmissionTeamRepository entrySubmissionTeamRepository;
    private final EntrySubmissionPlayerRepository entrySubmissionPlayerRepository;
    private final EntrySubmissionEntryRepository entrySubmissionEntryRepository;
    private final EntrySubmissionPermissionService entrySubmissionPermissionService;
    private final UserRepository userRepository;

    public EntrySubmissionSnapshotResponseDto getSnapshot(Long sessionId, EntrySubmissionActor actor) {
        EntrySubmissionSessionEntity session = requireSession(sessionId);
        List<EntrySubmissionTeamEntity> teams = entrySubmissionTeamRepository
                .findAllByEntrySubmissionSessionIdOrderByDisplayOrderAscIdAsc(sessionId);
        List<EntrySubmissionPlayerEntity> players = entrySubmissionPlayerRepository
                .findAllByEntrySubmissionSessionIdOrderByEntrySubmissionTeamIdAscDisplayOrderAscIdAsc(sessionId);
        List<EntrySubmissionEntryEntity> entries = entrySubmissionEntryRepository
                .findAllByEntrySubmissionSessionIdOrderBySetNoAscEntrySubmissionTeamIdAsc(sessionId);

        Map<Long, String> userLoginIds = loadUserLoginIds(session, teams, entries);
        Map<Long, EntrySubmissionPlayerEntity> playerMap = players.stream()
                .collect(LinkedHashMap::new, (map, player) -> map.put(player.getId(), player), Map::putAll);

        EntrySubmissionSnapshotResponseDto snapshot = new EntrySubmissionSnapshotResponseDto();
        snapshot.setSession(toSessionInfo(session, userLoginIds.get(session.getOwnerUserId())));
        snapshot.setTeams(teams.stream().map(team -> toTeamResponse(team, userLoginIds)).toList());
        snapshot.setPlayers(players.stream().map(this::toPlayerResponse).toList());
        snapshot.setEntries(entries.stream().map(entry -> toEntryResponse(entry, playerMap, userLoginIds)).toList());
        snapshot.setMatches(buildMatches(session.getSetCount(), teams, entries, playerMap));
        snapshot.setPermissions(buildPermissions(session, actor, teams));
        return snapshot;
    }

    public EntrySubmissionSnapshotResponseDto getBroadcastSnapshot(Long sessionId) {
        EntrySubmissionSnapshotResponseDto snapshot = getSnapshot(sessionId, null);
        snapshot.setPermissions(null);
        return snapshot;
    }

    private EntrySubmissionSessionEntity requireSession(Long sessionId) {
        return entrySubmissionSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Entry submission session could not be found."));
    }

    private Map<Long, String> loadUserLoginIds(
            EntrySubmissionSessionEntity session,
            List<EntrySubmissionTeamEntity> teams,
            List<EntrySubmissionEntryEntity> entries
    ) {
        List<Long> userIds = new ArrayList<>();
        userIds.add(session.getOwnerUserId());
        teams.forEach(team -> userIds.add(team.getCaptainUserId()));
        entries.forEach(entry -> userIds.add(entry.getSubmittedByUserId()));

        Map<Long, String> loginIds = new HashMap<>();
        for (UserEntity user : userRepository.findAllById(userIds.stream().filter(Objects::nonNull).distinct().toList())) {
            loginIds.put(user.getId(), user.getUserId());
        }
        return loginIds;
    }

    private EntrySubmissionSessionInfoResponseDto toSessionInfo(
            EntrySubmissionSessionEntity session,
            String ownerUserLoginId
    ) {
        EntrySubmissionSessionInfoResponseDto dto = new EntrySubmissionSessionInfoResponseDto();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setOwnerUserId(session.getOwnerUserId());
        dto.setOwnerUserLoginId(ownerUserLoginId);
        dto.setStatus(session.getStatus());
        dto.setSetCount(session.getSetCount());
        dto.setCompletedAt(session.getCompletedAt());
        dto.setRegDate(session.getRegDate());
        dto.setUpdateDate(session.getUpdateDate());
        dto.setServerNow(LocalDateTime.now());
        return dto;
    }

    private EntrySubmissionTeamResponseDto toTeamResponse(
            EntrySubmissionTeamEntity team,
            Map<Long, String> userLoginIds
    ) {
        EntrySubmissionTeamResponseDto dto = new EntrySubmissionTeamResponseDto();
        dto.setId(team.getId());
        dto.setEntrySubmissionSessionId(team.getEntrySubmissionSessionId());
        dto.setTeamName(team.getTeamName());
        dto.setDisplayOrder(team.getDisplayOrder());
        dto.setCaptainUserId(team.getCaptainUserId());
        dto.setCaptainUserLoginId(userLoginIds.get(team.getCaptainUserId()));
        dto.setSubmitted(team.getSubmittedAt() != null);
        dto.setSubmittedAt(team.getSubmittedAt());
        return dto;
    }

    private EntrySubmissionPlayerResponseDto toPlayerResponse(EntrySubmissionPlayerEntity player) {
        EntrySubmissionPlayerResponseDto dto = new EntrySubmissionPlayerResponseDto();
        dto.setId(player.getId());
        dto.setEntrySubmissionSessionId(player.getEntrySubmissionSessionId());
        dto.setEntrySubmissionTeamId(player.getEntrySubmissionTeamId());
        dto.setPlayerName(player.getPlayerName());
        dto.setDisplayOrder(player.getDisplayOrder());
        dto.setCaptain(EntrySubmissionPlayerEntity.CAPTAIN_Y.equals(player.getCaptainYn()));
        return dto;
    }

    private EntrySubmissionEntryResponseDto toEntryResponse(
            EntrySubmissionEntryEntity entry,
            Map<Long, EntrySubmissionPlayerEntity> playerMap,
            Map<Long, String> userLoginIds
    ) {
        EntrySubmissionPlayerEntity player = playerMap.get(entry.getEntrySubmissionPlayerId());
        EntrySubmissionEntryResponseDto dto = new EntrySubmissionEntryResponseDto();
        dto.setEntrySubmissionSessionId(entry.getEntrySubmissionSessionId());
        dto.setEntrySubmissionTeamId(entry.getEntrySubmissionTeamId());
        dto.setSetNo(entry.getSetNo());
        dto.setPlayerId(entry.getEntrySubmissionPlayerId());
        dto.setPlayerName(player != null ? player.getPlayerName() : null);
        dto.setSubmittedByUserId(entry.getSubmittedByUserId());
        dto.setSubmittedByUserLoginId(userLoginIds.get(entry.getSubmittedByUserId()));
        dto.setSubmittedAt(entry.getSubmittedAt());
        return dto;
    }

    private List<EntrySubmissionMatchResponseDto> buildMatches(
            int setCount,
            List<EntrySubmissionTeamEntity> teams,
            List<EntrySubmissionEntryEntity> entries,
            Map<Long, EntrySubmissionPlayerEntity> playerMap
    ) {
        Long team1Id = teams.stream()
                .filter(team -> team.getDisplayOrder() == 1)
                .map(EntrySubmissionTeamEntity::getId)
                .findFirst()
                .orElse(null);
        Long team2Id = teams.stream()
                .filter(team -> team.getDisplayOrder() == 2)
                .map(EntrySubmissionTeamEntity::getId)
                .findFirst()
                .orElse(null);
        Map<String, EntrySubmissionEntryEntity> entryMap = new HashMap<>();
        for (EntrySubmissionEntryEntity entry : entries) {
            entryMap.put(entry.getEntrySubmissionTeamId() + ":" + entry.getSetNo(), entry);
        }

        List<EntrySubmissionMatchResponseDto> matches = new ArrayList<>();
        for (int setNo = 1; setNo <= setCount; setNo++) {
            EntrySubmissionEntryEntity team1Entry = team1Id != null ? entryMap.get(team1Id + ":" + setNo) : null;
            EntrySubmissionEntryEntity team2Entry = team2Id != null ? entryMap.get(team2Id + ":" + setNo) : null;
            EntrySubmissionMatchResponseDto match = new EntrySubmissionMatchResponseDto();
            match.setSetNo(setNo);
            attachMatchPlayer(match, team1Entry, team2Entry, playerMap);
            matches.add(match);
        }
        return matches;
    }

    private void attachMatchPlayer(
            EntrySubmissionMatchResponseDto match,
            EntrySubmissionEntryEntity team1Entry,
            EntrySubmissionEntryEntity team2Entry,
            Map<Long, EntrySubmissionPlayerEntity> playerMap
    ) {
        if (team1Entry != null) {
            EntrySubmissionPlayerEntity player = playerMap.get(team1Entry.getEntrySubmissionPlayerId());
            match.setTeam1PlayerId(team1Entry.getEntrySubmissionPlayerId());
            match.setTeam1PlayerName(player != null ? player.getPlayerName() : null);
        }
        if (team2Entry != null) {
            EntrySubmissionPlayerEntity player = playerMap.get(team2Entry.getEntrySubmissionPlayerId());
            match.setTeam2PlayerId(team2Entry.getEntrySubmissionPlayerId());
            match.setTeam2PlayerName(player != null ? player.getPlayerName() : null);
        }
    }

    private EntrySubmissionPermissionsResponseDto buildPermissions(
            EntrySubmissionSessionEntity session,
            EntrySubmissionActor actor,
            List<EntrySubmissionTeamEntity> teams
    ) {
        EntrySubmissionPermissionsResponseDto permissions = new EntrySubmissionPermissionsResponseDto();
        if (actor == null || actor.userPk() == null) {
            return permissions;
        }

        boolean isOwner = entrySubmissionPermissionService.isOwner(session, actor);
        boolean isAdmin = entrySubmissionPermissionService.isAdmin(actor);
        EntrySubmissionTeamEntity myTeam = teams.stream()
                .filter(team -> Objects.equals(team.getCaptainUserId(), actor.userPk()))
                .findFirst()
                .orElse(null);

        permissions.setCanDelete(isOwner || isAdmin);
        permissions.setMyTeamId(myTeam != null ? myTeam.getId() : null);
        if (isOwner && myTeam != null) {
            permissions.setMyRole("OWNER_CAPTAIN");
        } else if (isOwner) {
            permissions.setMyRole("OWNER");
        } else if (myTeam != null) {
            permissions.setMyRole("CAPTAIN");
        }
        permissions.setCanSubmit(
                myTeam != null
                        && EntrySubmissionSessionEntity.STATUS_SUBMITTING.equals(session.getStatus())
                        && myTeam.getSubmittedAt() == null
        );
        return permissions;
    }
}
