package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionCreateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionDetailResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionQueryDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionSummaryResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftTeamResponseDto;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftTeamEntity;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftCandidateRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftPickRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftQueryRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftSessionRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RpsDraftService {

    private final RpsDraftSessionRepository rpsDraftSessionRepository;
    private final RpsDraftTeamRepository rpsDraftTeamRepository;
    private final RpsDraftCandidateRepository rpsDraftCandidateRepository;
    private final RpsDraftPickRepository rpsDraftPickRepository;
    private final RpsDraftQueryRepository rpsDraftQueryRepository;
    private final RpsDraftPermissionService rpsDraftPermissionService;
    private final UserRepository userRepository;

    public ResponseDto<RpsDraftSessionDetailResponseDto> createSession(
            RpsDraftSessionCreateRequestDto requestDto,
            RpsDraftActor actor
    ) {
        try {
            rpsDraftPermissionService.assertAuthenticated(actor);
            CreateSessionInputs inputs = prepareCreateSessionInputs(requestDto);

            RpsDraftSessionEntity session = rpsDraftSessionRepository.save(
                    RpsDraftSessionEntity.builder()
                            .title(requestDto.getTitle().trim())
                            .ownerUserId(actor.userPk())
                            .status(RpsDraftSessionEntity.STATUS_RPS_PENDING)
                            .startedAt(LocalDateTime.now())
                            .build()
            );

            rpsDraftTeamRepository.save(
                    RpsDraftTeamEntity.builder()
                            .rpsDraftSessionId(session.getId())
                            .teamName(buildTeamName(inputs.team1Picker()))
                            .displayOrder(1)
                            .pickerUserId(inputs.team1Picker().getId())
                            .build()
            );
            rpsDraftTeamRepository.save(
                    RpsDraftTeamEntity.builder()
                            .rpsDraftSessionId(session.getId())
                            .teamName(buildTeamName(inputs.team2Picker()))
                            .displayOrder(2)
                            .pickerUserId(inputs.team2Picker().getId())
                            .build()
            );

            rpsDraftCandidateRepository.saveAll(
                    buildCandidateEntities(session.getId(), inputs.candidateNames())
            );

            return ResponseDto.success(buildSessionDetail(session.getId()));
        } catch (Exception e) {
            log.error("Failed to create RPS draft session.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<RpsDraftSessionSummaryResponseDto>> listSessions() {
        try {
            return ResponseDto.success(
                    rpsDraftQueryRepository.findSessions().stream()
                            .map(this::toSessionSummary)
                            .toList()
            );
        } catch (Exception e) {
            log.error("Failed to list RPS draft sessions.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<RpsDraftSessionDetailResponseDto> getSession(Long sessionId) {
        try {
            return ResponseDto.success(buildSessionDetail(sessionId));
        } catch (Exception e) {
            log.error("Failed to get RPS draft session.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<RpsDraftTeamResponseDto>> listTeams(Long sessionId) {
        try {
            requireSession(sessionId);
            return ResponseDto.success(rpsDraftQueryRepository.findTeamsBySessionId(sessionId));
        } catch (Exception e) {
            log.error("Failed to list RPS draft teams.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<RpsDraftCandidateResponseDto>> listCandidates(Long sessionId) {
        try {
            requireSession(sessionId);
            return ResponseDto.success(rpsDraftQueryRepository.findCandidatesBySessionId(sessionId));
        } catch (Exception e) {
            log.error("Failed to list RPS draft candidates.", e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    public ResponseDto<Void> deleteSession(Long sessionId, RpsDraftActor actor) {
        try {
            if (actor == null || actor.userPk() == null) {
                return ResponseDto.fail(HttpServletResponse.SC_FORBIDDEN, "Authentication is required.");
            }

            RpsDraftSessionEntity session = rpsDraftSessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null) {
                return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "RPS draft session could not be found.");
            }

            rpsDraftPermissionService.assertOwnerOrAdmin(session, actor);

            RpsDraftSessionDeleteStats deleteStats = collectDeleteStats(sessionId, session);
            log.info(
                    "Deleting RPS draft session. sessionId={}, status={}, currentDraftTeamId={}, pendingDraftTeamId={}, picks={}, candidates={}, teams={}",
                    deleteStats.sessionId(),
                    deleteStats.status(),
                    deleteStats.currentDraftTeamId(),
                    deleteStats.pendingDraftTeamId(),
                    deleteStats.pickCount(),
                    deleteStats.candidateCount(),
                    deleteStats.teamCount()
            );

            session.clearProgressState();
            rpsDraftSessionRepository.flush();

            int deletedPicks = rpsDraftPickRepository.deleteByRpsDraftSessionId(sessionId);
            int deletedCandidates = rpsDraftCandidateRepository.deleteByRpsDraftSessionId(sessionId);
            int deletedTeams = rpsDraftTeamRepository.deleteByRpsDraftSessionId(sessionId);
            rpsDraftSessionRepository.delete(session);

            log.info(
                    "Deleted RPS draft session. sessionId={}, deletedPicks={}, deletedCandidates={}, deletedTeams={}",
                    sessionId,
                    deletedPicks,
                    deletedCandidates,
                    deletedTeams
            );

            return ResponseDto.success(null);
        } catch (SecurityException e) {
            log.warn("Denied deleting RPS draft session. sessionId={}, actor={}", sessionId, actor, e);
            return ResponseDto.fail(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete RPS draft session. sessionId={}", sessionId, e);
            return ResponseDto.fail(e.getMessage());
        }
    }

    private RpsDraftSessionDetailResponseDto buildSessionDetail(Long sessionId) {
        RpsDraftSessionQueryDto session = requireSessionQuery(sessionId);

        RpsDraftSessionDetailResponseDto detail = new RpsDraftSessionDetailResponseDto();
        detail.setId(session.getId());
        detail.setTitle(session.getTitle());
        detail.setOwnerUserId(session.getOwnerUserId());
        detail.setOwnerUserLoginId(session.getOwnerUserLoginId());
        detail.setOwnerName(session.getOwnerName());
        detail.setStatus(session.getStatus());
        detail.setCurrentPickNo(session.getCurrentPickNo());
        detail.setCurrentDraftTeamId(session.getCurrentDraftTeamId());
        detail.setPendingDraftTeamId(session.getPendingDraftTeamId());
        detail.setStartedAt(session.getStartedAt());
        detail.setEndedAt(session.getEndedAt());
        detail.setRegDate(session.getRegDate());
        detail.setUpdateDate(session.getUpdateDate());
        detail.setTeams(rpsDraftQueryRepository.findTeamsBySessionId(sessionId));
        detail.setCandidates(rpsDraftQueryRepository.findCandidatesBySessionId(sessionId));
        return detail;
    }

    public RpsDraftSessionDetailResponseDto requireSessionDetail(Long sessionId) {
        return buildSessionDetail(sessionId);
    }

    private RpsDraftSessionSummaryResponseDto toSessionSummary(RpsDraftSessionQueryDto session) {
        RpsDraftSessionSummaryResponseDto summary = new RpsDraftSessionSummaryResponseDto();
        summary.setId(session.getId());
        summary.setTitle(session.getTitle());
        summary.setOwnerUserId(session.getOwnerUserId());
        summary.setOwnerUserLoginId(session.getOwnerUserLoginId());
        summary.setOwnerName(session.getOwnerName());
        summary.setStatus(session.getStatus());
        summary.setCurrentPickNo(session.getCurrentPickNo());
        summary.setCurrentDraftTeamId(session.getCurrentDraftTeamId());
        summary.setPendingDraftTeamId(session.getPendingDraftTeamId());
        summary.setStartedAt(session.getStartedAt());
        summary.setEndedAt(session.getEndedAt());
        summary.setRegDate(session.getRegDate());
        summary.setUpdateDate(session.getUpdateDate());
        return summary;
    }

    private RpsDraftSessionEntity requireSession(Long sessionId) {
        return rpsDraftSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("RPS draft session could not be found."));
    }

    private RpsDraftSessionQueryDto requireSessionQuery(Long sessionId) {
        return rpsDraftQueryRepository.findSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("RPS draft session could not be found."));
    }

    private RpsDraftSessionDeleteStats collectDeleteStats(Long sessionId, RpsDraftSessionEntity session) {
        return new RpsDraftSessionDeleteStats(
                sessionId,
                session.getStatus(),
                session.getCurrentDraftTeamId(),
                session.getPendingDraftTeamId(),
                rpsDraftTeamRepository.countByRpsDraftSessionId(sessionId),
                rpsDraftCandidateRepository.countByRpsDraftSessionId(sessionId),
                rpsDraftPickRepository.countByRpsDraftSessionId(sessionId)
        );
    }

    private void validateSessionRequest(RpsDraftSessionCreateRequestDto requestDto) {
        if (requestDto == null) {
            throw new IllegalArgumentException("Session request is required.");
        }
        if (requestDto.getTitle() == null || requestDto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Session title is required.");
        }
        if (requestDto.getTeam1PickerUserId() == null) {
            throw new IllegalArgumentException("Team 1 picker user id is required.");
        }
        if (requestDto.getTeam2PickerUserId() == null) {
            throw new IllegalArgumentException("Team 2 picker user id is required.");
        }
        if (Objects.equals(requestDto.getTeam1PickerUserId(), requestDto.getTeam2PickerUserId())) {
            throw new IllegalArgumentException("Two distinct pickers must be selected.");
        }
    }

    private void validateDistinctTeamNames(String team1Name, String team2Name) {
        if (Objects.equals(team1Name, team2Name)) {
            throw new IllegalArgumentException("Team names must be different.");
        }
    }

    private CreateSessionInputs prepareCreateSessionInputs(RpsDraftSessionCreateRequestDto requestDto) {
        validateSessionRequest(requestDto);

        UserEntity team1Picker = requireActivePickerUser(
                requestDto.getTeam1PickerUserId(),
                "Team 1 picker user could not be found."
        );
        UserEntity team2Picker = requireActivePickerUser(
                requestDto.getTeam2PickerUserId(),
                "Team 2 picker user could not be found."
        );

        String team1Name = buildTeamName(team1Picker);
        String team2Name = buildTeamName(team2Picker);
        validateDistinctTeamNames(team1Name, team2Name);

        return new CreateSessionInputs(
                team1Picker,
                team2Picker,
                normalizeCandidateNames(requestDto.getCandidateNames())
        );
    }

    private UserEntity requireActivePickerUser(Long pickerUserId, String notFoundMessage) {
        UserEntity pickerUser = userRepository.findById(pickerUserId)
                .orElseThrow(() -> new IllegalArgumentException(notFoundMessage));
        if (!"ACTIVE".equals(pickerUser.getStatus())) {
            throw new IllegalArgumentException("Only ACTIVE users can be assigned as pickers.");
        }
        return pickerUser;
    }

    private List<String> normalizeCandidateNames(List<String> candidateNames) {
        if (candidateNames == null || candidateNames.isEmpty()) {
            throw new IllegalArgumentException("At least one candidate name is required.");
        }

        List<String> normalizedNames = new ArrayList<>();
        Set<String> uniqueNames = new LinkedHashSet<>();

        for (String candidateName : candidateNames) {
            if (candidateName == null) {
                continue;
            }

            String normalizedName = candidateName.trim();
            if (normalizedName.isEmpty()) {
                continue;
            }

            String uniqueKey = normalizedName.toLowerCase(Locale.ROOT);
            if (!uniqueNames.add(uniqueKey)) {
                throw new IllegalArgumentException("Duplicate candidate names are not allowed.");
            }

            normalizedNames.add(normalizedName);
        }

        if (normalizedNames.isEmpty()) {
            throw new IllegalArgumentException("At least one candidate name is required.");
        }

        return normalizedNames;
    }

    private String buildTeamName(UserEntity pickerUser) {
        if (pickerUser.getUserId() != null && !pickerUser.getUserId().isBlank()) {
            return pickerUser.getUserId().trim();
        }
        throw new IllegalArgumentException("Picker user id is required for team naming.");
    }

    private List<RpsDraftCandidateEntity> buildCandidateEntities(
            Long sessionId,
            List<String> candidateNames
    ) {
        List<RpsDraftCandidateEntity> candidates = new ArrayList<>(candidateNames.size());
        for (int index = 0; index < candidateNames.size(); index++) {
            candidates.add(RpsDraftCandidateEntity.builder()
                    .rpsDraftSessionId(sessionId)
                    .candidateName(candidateNames.get(index))
                    .displayOrder(index + 1)
                    .build());
        }
        return candidates;
    }

    private record CreateSessionInputs(
            UserEntity team1Picker,
            UserEntity team2Picker,
            List<String> candidateNames
    ) {
    }

    private record RpsDraftSessionDeleteStats(
            Long sessionId,
            String status,
            Long currentDraftTeamId,
            Long pendingDraftTeamId,
            long teamCount,
            long candidateCount,
            long pickCount
    ) {
    }
}
