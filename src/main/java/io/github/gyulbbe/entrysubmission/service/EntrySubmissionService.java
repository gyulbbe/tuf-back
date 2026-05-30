package io.github.gyulbbe.entrysubmission.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.entrysubmission.auth.EntrySubmissionActor;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSessionCreateRequestDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSessionSummaryResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSnapshotResponseDto;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionPlayerEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionSessionEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionTeamEntity;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionEntryRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionPlayerRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionSessionRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EntrySubmissionService {

    private final EntrySubmissionSessionRepository entrySubmissionSessionRepository;
    private final EntrySubmissionTeamRepository entrySubmissionTeamRepository;
    private final EntrySubmissionPlayerRepository entrySubmissionPlayerRepository;
    private final EntrySubmissionEntryRepository entrySubmissionEntryRepository;
    private final EntrySubmissionPermissionService entrySubmissionPermissionService;
    private final EntrySubmissionSnapshotService entrySubmissionSnapshotService;
    private final UserRepository userRepository;

    public ResponseDto<EntrySubmissionSnapshotResponseDto> createSession(
            EntrySubmissionSessionCreateRequestDto requestDto,
            EntrySubmissionActor actor
    ) {
        try {
            entrySubmissionPermissionService.assertAuthenticated(actor);
            CreateInputs inputs = prepareCreateInputs(requestDto);

            EntrySubmissionSessionEntity session = entrySubmissionSessionRepository.save(
                    EntrySubmissionSessionEntity.builder()
                            .title(requestDto.getTitle().trim())
                            .ownerUserId(actor.userPk())
                            .status(EntrySubmissionSessionEntity.STATUS_SUBMITTING)
                            .setCount(inputs.setCount())
                            .build()
            );

            EntrySubmissionTeamEntity team1 = entrySubmissionTeamRepository.save(
                    EntrySubmissionTeamEntity.builder()
                            .entrySubmissionSessionId(session.getId())
                            .teamName(buildTeamName(inputs.team1Captain()))
                            .displayOrder(1)
                            .captainUserId(inputs.team1Captain().getId())
                            .build()
            );
            EntrySubmissionTeamEntity team2 = entrySubmissionTeamRepository.save(
                    EntrySubmissionTeamEntity.builder()
                            .entrySubmissionSessionId(session.getId())
                            .teamName(buildTeamName(inputs.team2Captain()))
                            .displayOrder(2)
                            .captainUserId(inputs.team2Captain().getId())
                            .build()
            );

            entrySubmissionPlayerRepository.saveAll(buildPlayers(session.getId(), team1.getId(), inputs.team1Players()));
            entrySubmissionPlayerRepository.saveAll(buildPlayers(session.getId(), team2.getId(), inputs.team2Players()));

            return ResponseDto.success(entrySubmissionSnapshotService.getSnapshot(session.getId(), actor));
        } catch (Exception e) {
            log.error("Failed to create entry submission session.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<EntrySubmissionSessionSummaryResponseDto>> listSessions() {
        try {
            List<EntrySubmissionSessionEntity> sessions = entrySubmissionSessionRepository.findAllByOrderByRegDateDescIdDesc();
            Set<Long> ownerIds = sessions.stream()
                    .map(EntrySubmissionSessionEntity::getOwnerUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            var owners = userRepository.findAllById(ownerIds).stream()
                    .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (left, right) -> left));

            return ResponseDto.success(
                    sessions.stream()
                            .sorted(
                                    Comparator.comparing(
                                                    (EntrySubmissionSessionEntity session) ->
                                                            EntrySubmissionSessionEntity.STATUS_COMPLETED.equals(session.getStatus())
                                            )
                                            .thenComparing(
                                                    EntrySubmissionSessionEntity::getRegDate,
                                                    Comparator.nullsLast(Comparator.reverseOrder())
                                            )
                                            .thenComparing(EntrySubmissionSessionEntity::getId, Comparator.reverseOrder())
                            )
                            .map(session -> toSummary(session, owners.get(session.getOwnerUserId())))
                            .toList()
            );
        } catch (Exception e) {
            log.error("Failed to list entry submission sessions.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deleteSession(Long sessionId, EntrySubmissionActor actor) {
        try {
            if (actor == null || actor.userPk() == null) {
                return ResponseDto.fail(HttpServletResponse.SC_FORBIDDEN, "Authentication is required.");
            }

            EntrySubmissionSessionEntity session = entrySubmissionSessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null) {
                return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "Entry submission session could not be found.");
            }

            entrySubmissionPermissionService.assertOwnerOrAdmin(session, actor);

            int deletedEntries = entrySubmissionEntryRepository.deleteByEntrySubmissionSessionId(sessionId);
            int deletedPlayers = entrySubmissionPlayerRepository.deleteByEntrySubmissionSessionId(sessionId);
            int deletedTeams = entrySubmissionTeamRepository.deleteByEntrySubmissionSessionId(sessionId);
            entrySubmissionSessionRepository.delete(session);

            log.info(
                    "Deleted entry submission session. sessionId={}, deletedEntries={}, deletedPlayers={}, deletedTeams={}",
                    sessionId,
                    deletedEntries,
                    deletedPlayers,
                    deletedTeams
            );

            return ResponseDto.success(null);
        } catch (SecurityException e) {
            log.warn("Denied deleting entry submission session. sessionId={}, actor={}", sessionId, actor, e);
            return ResponseDto.fail(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete entry submission session. sessionId={}", sessionId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    private EntrySubmissionSessionSummaryResponseDto toSummary(
            EntrySubmissionSessionEntity session,
            UserEntity owner
    ) {
        EntrySubmissionSessionSummaryResponseDto dto = new EntrySubmissionSessionSummaryResponseDto();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setOwnerUserId(session.getOwnerUserId());
        dto.setOwnerUserLoginId(owner != null ? owner.getUserId() : null);
        dto.setStatus(session.getStatus());
        dto.setSetCount(session.getSetCount());
        dto.setCompletedAt(session.getCompletedAt());
        dto.setRegDate(session.getRegDate());
        dto.setUpdateDate(session.getUpdateDate());
        return dto;
    }

    private CreateInputs prepareCreateInputs(EntrySubmissionSessionCreateRequestDto requestDto) {
        validateCreateRequest(requestDto);

        UserEntity team1Captain = requireActiveUser(requestDto.getTeam1CaptainUserId(), "Team 1 captain user could not be found.");
        UserEntity team2Captain = requireActiveUser(requestDto.getTeam2CaptainUserId(), "Team 2 captain user could not be found.");
        if (Objects.equals(team1Captain.getId(), team2Captain.getId())) {
            throw new IllegalArgumentException("Two distinct captains must be selected.");
        }

        List<String> team1Players = buildPlayerNames(team1Captain, requestDto.getTeam1PlayerNames());
        List<String> team2Players = buildPlayerNames(team2Captain, requestDto.getTeam2PlayerNames());
        int setCount = requestDto.getSetCount() != null
                ? validateSetCount(requestDto.getSetCount())
                : Math.max(team1Players.size(), team2Players.size());

        return new CreateInputs(team1Captain, team2Captain, team1Players, team2Players, setCount);
    }

    private void validateCreateRequest(EntrySubmissionSessionCreateRequestDto requestDto) {
        if (requestDto == null) {
            throw new IllegalArgumentException("Entry submission request is required.");
        }
        if (requestDto.getTitle() == null || requestDto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (requestDto.getTeam1CaptainUserId() == null) {
            throw new IllegalArgumentException("Team 1 captain user id is required.");
        }
        if (requestDto.getTeam2CaptainUserId() == null) {
            throw new IllegalArgumentException("Team 2 captain user id is required.");
        }
    }

    private UserEntity requireActiveUser(Long userId, String message) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(message));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("Only ACTIVE users can be assigned as captains.");
        }
        return user;
    }

    private int validateSetCount(Integer setCount) {
        if (setCount == null || setCount < 1) {
            throw new IllegalArgumentException("Set count must be greater than zero.");
        }
        return setCount;
    }

    private List<String> buildPlayerNames(UserEntity captain, List<String> playerNames) {
        String captainName = buildTeamName(captain);
        List<String> names = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();
        names.add(captainName);
        uniqueKeys.add(captainName.toLowerCase(Locale.ROOT));

        if (playerNames == null) {
            return names;
        }

        for (String playerName : playerNames) {
            if (playerName == null || playerName.isBlank()) {
                throw new IllegalArgumentException("Player name cannot be blank.");
            }

            String normalizedName = playerName.trim();
            String key = normalizedName.toLowerCase(Locale.ROOT);
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate player names are not allowed in the same team.");
            }
            names.add(normalizedName);
        }

        return names;
    }

    private String buildTeamName(UserEntity captain) {
        if (captain.getUserId() != null && !captain.getUserId().isBlank()) {
            return captain.getUserId().trim();
        }
        throw new IllegalArgumentException("Captain user id is required.");
    }

    private List<EntrySubmissionPlayerEntity> buildPlayers(
            Long sessionId,
            Long teamId,
            List<String> playerNames
    ) {
        List<EntrySubmissionPlayerEntity> players = new ArrayList<>();
        for (int index = 0; index < playerNames.size(); index++) {
            players.add(EntrySubmissionPlayerEntity.builder()
                    .entrySubmissionSessionId(sessionId)
                    .entrySubmissionTeamId(teamId)
                    .playerName(playerNames.get(index))
                    .displayOrder(index + 1)
                    .captainYn(index == 0 ? EntrySubmissionPlayerEntity.CAPTAIN_Y : EntrySubmissionPlayerEntity.CAPTAIN_N)
                    .build());
        }
        return players;
    }

    private record CreateInputs(
            UserEntity team1Captain,
            UserEntity team2Captain,
            List<String> team1Players,
            List<String> team2Players,
            int setCount
    ) {
    }
}
