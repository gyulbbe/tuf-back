package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionCreateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionDetailResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionQueryDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionSummaryResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftTeamResponseDto;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateId;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftTeamEntity;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftCandidateRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftQueryRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftSessionRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RpsDraftService {

    private static final Set<String> RACES = Set.of("ZERG", "TERRAN", "PROTOSS", "RANDOM");

    private final RpsDraftSessionRepository rpsDraftSessionRepository;
    private final RpsDraftTeamRepository rpsDraftTeamRepository;
    private final RpsDraftCandidateRepository rpsDraftCandidateRepository;
    private final RpsDraftQueryRepository rpsDraftQueryRepository;
    private final RpsDraftPermissionService rpsDraftPermissionService;
    private final UserRepository userRepository;

    public ResponseDto<RpsDraftSessionDetailResponseDto> createSession(
            RpsDraftSessionCreateRequestDto requestDto,
            RpsDraftActor actor
    ) {
        try {
            rpsDraftPermissionService.assertAuthenticated(actor);
            validateSessionRequest(requestDto);

            String team1Name = normalizeTeamName(requestDto.getTeam1Name(), "1팀");
            String team2Name = normalizeTeamName(requestDto.getTeam2Name(), "2팀");
            validateDistinctTeamNames(team1Name, team2Name);

            RpsDraftSessionEntity session = rpsDraftSessionRepository.save(
                    RpsDraftSessionEntity.builder()
                            .title(requestDto.getTitle().trim())
                            .ownerUserId(actor.userPk())
                            .build()
            );

            rpsDraftTeamRepository.save(
                    RpsDraftTeamEntity.builder()
                            .rpsDraftSessionId(session.getId())
                            .teamName(team1Name)
                            .displayOrder(1)
                            .build()
            );
            rpsDraftTeamRepository.save(
                    RpsDraftTeamEntity.builder()
                            .rpsDraftSessionId(session.getId())
                            .teamName(team2Name)
                            .displayOrder(2)
                            .build()
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

    public ResponseDto<RpsDraftCandidateResponseDto> registerCandidate(
            Long sessionId,
            RpsDraftCandidateRequestDto requestDto,
            RpsDraftActor actor
    ) {
        try {
            RpsDraftSessionEntity session = requireSession(sessionId);
            rpsDraftPermissionService.assertOwner(session, actor);
            assertReadySession(session);
            validateCandidateRequest(requestDto);

            if (rpsDraftCandidateRepository.existsByRpsDraftSessionIdAndCandidateUserId(sessionId, requestDto.getCandidateUserId())) {
                throw new IllegalArgumentException("Candidate already exists in this session.");
            }

            UserEntity candidateUser = userRepository.findById(requestDto.getCandidateUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Candidate user could not be found."));

            String candidateName = normalizeCandidateName(requestDto.getCandidateName(), candidateUser);
            String race = normalizeRace(requestDto.getRace(), candidateUser.getRace());

            rpsDraftCandidateRepository.save(
                    RpsDraftCandidateEntity.builder()
                            .rpsDraftSessionId(sessionId)
                            .candidateUserId(requestDto.getCandidateUserId())
                            .candidateName(candidateName)
                            .race(race)
                            .build()
            );

            return ResponseDto.success(requireCandidate(sessionId, requestDto.getCandidateUserId()));
        } catch (Exception e) {
            log.error("Failed to register RPS draft candidate.", e);
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

    private RpsDraftSessionDetailResponseDto buildSessionDetail(Long sessionId) {
        RpsDraftSessionQueryDto session = requireSessionQuery(sessionId);

        RpsDraftSessionDetailResponseDto detail = new RpsDraftSessionDetailResponseDto();
        detail.setId(session.getId());
        detail.setTitle(session.getTitle());
        detail.setOwnerUserId(session.getOwnerUserId());
        detail.setOwnerName(session.getOwnerName());
        detail.setStatus(session.getStatus());
        detail.setCurrentPickNo(session.getCurrentPickNo());
        detail.setCurrentDraftTeamId(session.getCurrentDraftTeamId());
        detail.setPendingDraftTeamId(session.getPendingDraftTeamId());
        detail.setStartedAt(session.getStartedAt());
        detail.setEndedAt(session.getEndedAt());
        detail.setTeams(rpsDraftQueryRepository.findTeamsBySessionId(sessionId));
        return detail;
    }

    private RpsDraftSessionSummaryResponseDto toSessionSummary(RpsDraftSessionQueryDto session) {
        RpsDraftSessionSummaryResponseDto summary = new RpsDraftSessionSummaryResponseDto();
        summary.setId(session.getId());
        summary.setTitle(session.getTitle());
        summary.setOwnerUserId(session.getOwnerUserId());
        summary.setOwnerName(session.getOwnerName());
        summary.setStatus(session.getStatus());
        summary.setCurrentPickNo(session.getCurrentPickNo());
        summary.setCurrentDraftTeamId(session.getCurrentDraftTeamId());
        summary.setPendingDraftTeamId(session.getPendingDraftTeamId());
        summary.setStartedAt(session.getStartedAt());
        summary.setEndedAt(session.getEndedAt());
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

    private RpsDraftCandidateResponseDto requireCandidate(Long sessionId, Long candidateUserId) {
        return rpsDraftQueryRepository.findCandidate(sessionId, candidateUserId)
                .orElseThrow(() -> new IllegalArgumentException("RPS draft candidate could not be found."));
    }

    private void validateSessionRequest(RpsDraftSessionCreateRequestDto requestDto) {
        if (requestDto == null || requestDto.getTitle() == null || requestDto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Session title is required.");
        }
    }

    private void validateCandidateRequest(RpsDraftCandidateRequestDto requestDto) {
        if (requestDto == null || requestDto.getCandidateUserId() == null) {
            throw new IllegalArgumentException("Candidate user id is required.");
        }
        if (requestDto.getRace() != null && !requestDto.getRace().isBlank() && !RACES.contains(requestDto.getRace())) {
            throw new IllegalArgumentException("Candidate race is invalid.");
        }
    }

    private void validateDistinctTeamNames(String team1Name, String team2Name) {
        if (Objects.equals(team1Name, team2Name)) {
            throw new IllegalArgumentException("Team names must be different.");
        }
    }

    private String normalizeTeamName(String requestedName, String defaultName) {
        if (requestedName == null || requestedName.isBlank()) {
            return defaultName;
        }
        return requestedName.trim();
    }

    private String normalizeCandidateName(String requestedName, UserEntity candidateUser) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        if (candidateUser.getName() != null && !candidateUser.getName().isBlank()) {
            return candidateUser.getName();
        }
        if (candidateUser.getUserId() != null && !candidateUser.getUserId().isBlank()) {
            return candidateUser.getUserId();
        }
        throw new IllegalArgumentException("Candidate name is required.");
    }

    private String normalizeRace(String requestedRace, String fallbackRace) {
        String race = requestedRace != null && !requestedRace.isBlank() ? requestedRace : fallbackRace;
        if (race == null || race.isBlank()) {
            return null;
        }
        if (!RACES.contains(race)) {
            throw new IllegalArgumentException("Candidate race is invalid.");
        }
        return race;
    }

    private void assertReadySession(RpsDraftSessionEntity session) {
        if (!RpsDraftSessionEntity.STATUS_READY.equals(session.getStatus())) {
            throw new IllegalArgumentException("Only READY sessions can be updated.");
        }
    }
}
