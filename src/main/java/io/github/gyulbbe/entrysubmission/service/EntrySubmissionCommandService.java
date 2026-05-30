package io.github.gyulbbe.entrysubmission.service;

import io.github.gyulbbe.entrysubmission.auth.EntrySubmissionActor;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionEntryRequestDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionEventType;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSnapshotResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSubmitRequestDto;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionEntryEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionPlayerEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionSessionEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionTeamEntity;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionEntryRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionPlayerRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionSessionRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionTeamRepository;
import io.github.gyulbbe.entrysubmission.ws.EntrySubmissionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class EntrySubmissionCommandService {

    private final EntrySubmissionSessionRepository entrySubmissionSessionRepository;
    private final EntrySubmissionTeamRepository entrySubmissionTeamRepository;
    private final EntrySubmissionPlayerRepository entrySubmissionPlayerRepository;
    private final EntrySubmissionEntryRepository entrySubmissionEntryRepository;
    private final EntrySubmissionPermissionService entrySubmissionPermissionService;
    private final EntrySubmissionSnapshotService entrySubmissionSnapshotService;
    private final EntrySubmissionEventPublisher entrySubmissionEventPublisher;

    public EntrySubmissionSnapshotResponseDto submitEntries(
            Long sessionId,
            EntrySubmissionSubmitRequestDto requestDto,
            EntrySubmissionActor actor
    ) {
        entrySubmissionPermissionService.assertAuthenticated(actor);

        EntrySubmissionSessionEntity session = entrySubmissionSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Entry submission session could not be found."));
        if (EntrySubmissionSessionEntity.STATUS_COMPLETED.equals(session.getStatus())) {
            throw new IllegalArgumentException("Entry submission session is already completed.");
        }

        EntrySubmissionTeamEntity actorTeam = entrySubmissionPermissionService
                .findCaptainTeam(sessionId, actor.userPk())
                .orElseThrow(() -> new IllegalArgumentException("Only a captain assigned to this session can submit entries."));
        if (actorTeam.getSubmittedAt() != null) {
            throw new IllegalArgumentException("This team has already submitted entries.");
        }

        List<EntrySubmissionPlayerEntity> teamPlayers = entrySubmissionPlayerRepository
                .findAllByEntrySubmissionTeamIdOrderByDisplayOrderAscIdAsc(actorTeam.getId());
        Map<Long, EntrySubmissionPlayerEntity> playerMap = teamPlayers.stream()
                .collect(Collectors.toMap(EntrySubmissionPlayerEntity::getId, Function.identity(), (left, right) -> left));
        List<EntrySubmissionEntryEntity> entries = buildEntries(session, actorTeam, playerMap, requestDto, actor.userPk());

        LocalDateTime now = LocalDateTime.now();
        entrySubmissionEntryRepository.deleteByEntrySubmissionSessionIdAndEntrySubmissionTeamId(sessionId, actorTeam.getId());
        entrySubmissionEntryRepository.saveAll(entries);
        actorTeam.markSubmitted(now);

        List<EntrySubmissionTeamEntity> teams = entrySubmissionTeamRepository
                .findAllByEntrySubmissionSessionIdOrderByDisplayOrderAscIdAsc(sessionId);
        boolean completed = teams.stream().allMatch(team -> team.getSubmittedAt() != null);
        EntrySubmissionEventType eventType = EntrySubmissionEventType.TEAM_SUBMITTED;
        String message = "Entry submitted.";
        if (completed) {
            session.complete(now);
            eventType = EntrySubmissionEventType.SESSION_COMPLETED;
            message = "Entry submission completed.";
        }

        EntrySubmissionSnapshotResponseDto snapshot = entrySubmissionSnapshotService.getSnapshot(sessionId, actor);
        entrySubmissionEventPublisher.publishAfterCommit(sessionId, eventType, actor, message);
        return snapshot;
    }

    private List<EntrySubmissionEntryEntity> buildEntries(
            EntrySubmissionSessionEntity session,
            EntrySubmissionTeamEntity actorTeam,
            Map<Long, EntrySubmissionPlayerEntity> playerMap,
            EntrySubmissionSubmitRequestDto requestDto,
            Long submittedByUserId
    ) {
        if (requestDto == null || requestDto.getEntries() == null) {
            throw new IllegalArgumentException("Entries are required.");
        }
        if (requestDto.getEntries().size() != session.getSetCount()) {
            throw new IllegalArgumentException("One entry is required for every set.");
        }

        Map<Integer, Long> setPlayerMap = new LinkedHashMap<>();
        for (EntrySubmissionEntryRequestDto entry : requestDto.getEntries()) {
            if (entry == null || entry.getSetNo() == null || entry.getPlayerId() == null) {
                throw new IllegalArgumentException("Set number and player id are required.");
            }
            if (entry.getSetNo() < 1 || entry.getSetNo() > session.getSetCount()) {
                throw new IllegalArgumentException("Set number is out of range.");
            }
            if (setPlayerMap.put(entry.getSetNo(), entry.getPlayerId()) != null) {
                throw new IllegalArgumentException("Duplicate set entries are not allowed.");
            }
            EntrySubmissionPlayerEntity player = playerMap.get(entry.getPlayerId());
            if (player == null || !Objects.equals(player.getEntrySubmissionTeamId(), actorTeam.getId())) {
                throw new IllegalArgumentException("Only players from your team can be submitted.");
            }
        }

        if (setPlayerMap.size() != session.getSetCount()) {
            throw new IllegalArgumentException("One entry is required for every set.");
        }

        boolean repeatAllowed = session.getSetCount() > playerMap.size();
        if (!repeatAllowed) {
            Set<Long> uniquePlayerIds = new HashSet<>(setPlayerMap.values());
            if (uniquePlayerIds.size() != setPlayerMap.size()) {
                throw new IllegalArgumentException("Duplicate players are not allowed unless set count exceeds team player count.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<EntrySubmissionEntryEntity> entries = new ArrayList<>();
        for (int setNo = 1; setNo <= session.getSetCount(); setNo++) {
            Long playerId = setPlayerMap.get(setNo);
            if (playerId == null) {
                throw new IllegalArgumentException("One entry is required for every set.");
            }
            entries.add(EntrySubmissionEntryEntity.builder()
                    .entrySubmissionSessionId(session.getId())
                    .entrySubmissionTeamId(actorTeam.getId())
                    .setNo(setNo)
                    .entrySubmissionPlayerId(playerId)
                    .submittedByUserId(submittedByUserId)
                    .submittedAt(now)
                    .build());
        }
        return entries;
    }
}
