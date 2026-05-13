package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentMatchScoreRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionRejectRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchScoreSubmissionEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentMatchScoreSubmissionService {

    private static final Set<String> ADMIN_ROLES = Set.of("ROLE_MANAGER", "ROLE_MASTER", "ROLE_ADMIN");
    private static final String AUTO_REJECT_NOTE = "Another score submission was approved.";

    private final TournamentRepository tournamentRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentStageRepository stageRepository;
    private final TournamentMatchSlotRepository matchSlotRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentMatchScoreSubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final TournamentBracketProgressionService progressionService;
    private final TournamentService tournamentService;

    @Transactional
    public TournamentScoreSubmissionResponseDto submitScore(
            Long tournamentId,
            Long matchId,
            TournamentScoreSubmissionRequestDto request,
            Long actorUserId,
            String actorRole
    ) {
        MatchContext context = loadContext(tournamentId, matchId);
        validateScoreSubmittableMatch(context);

        Long submittedByParticipantId = resolveSubmitterParticipantId(context, actorUserId, actorRole);
        ScoreDecision decision = decideScore(context.match(), request, context.slotsByNo());
        String submitterRole = isAdmin(actorRole)
                ? TournamentMatchScoreSubmissionEntity.ROLE_ADMIN
                : TournamentMatchScoreSubmissionEntity.ROLE_PLAYER;

        TournamentMatchScoreSubmissionEntity submission = TournamentMatchScoreSubmissionEntity.builder()
                .tournamentId(tournamentId)
                .matchId(matchId)
                .submittedByUserId(actorUserId)
                .submittedByParticipantId(submittedByParticipantId)
                .submitterRole(submitterRole)
                .slot1Score(decision.slot1Score())
                .slot2Score(decision.slot2Score())
                .winnerSlotNo(decision.winnerSlotNo())
                .status(TournamentMatchScoreSubmissionEntity.STATUS_PENDING)
                .build();

        TournamentMatchScoreSubmissionEntity savedSubmission = submissionRepository.save(submission);
        return toResponse(savedSubmission, loadSubmitterLoginIds(List.of(savedSubmission)));
    }

    @Transactional(readOnly = true)
    public List<TournamentScoreSubmissionResponseDto> listSubmissions(
            Long tournamentId,
            Long matchId,
            Long actorUserId,
            String actorRole
    ) {
        MatchContext context = loadContext(tournamentId, matchId);
        requireSubmissionViewer(context, actorUserId, actorRole);

        List<TournamentMatchScoreSubmissionEntity> submissions = submissionRepository
                .findAllByTournamentIdAndMatchIdOrderByRegDateDescIdDesc(tournamentId, matchId);
        Map<Long, String> submitterLoginIds = loadSubmitterLoginIds(submissions);

        return submissions
                .stream()
                .map(submission -> toResponse(submission, submitterLoginIds))
                .toList();
    }

    @Transactional
    public TournamentDetailResponseDto approveSubmission(
            Long tournamentId,
            Long matchId,
            Long submissionId,
            Long adminUserId,
            String actorRole
    ) {
        requireAdmin(actorRole);
        MatchContext context = loadContext(tournamentId, matchId);
        validateScoreSubmittableMatch(context);

        TournamentMatchScoreSubmissionEntity submission = findSubmission(tournamentId, matchId, submissionId);
        if (!TournamentMatchScoreSubmissionEntity.STATUS_PENDING.equals(submission.getStatus())) {
            throw invalid("Only PENDING score submissions can be approved.");
        }

        ScoreDecision decision = decideStoredScore(context.match(), submission);
        if (!Objects.equals(submission.getWinnerSlotNo(), decision.winnerSlotNo())) {
            throw invalid("Stored winner slot does not match submission scores.");
        }

        TournamentMatchSlotEntity winnerSlot = context.slotsByNo().get(decision.winnerSlotNo());
        TournamentMatchSlotEntity loserSlot = context.slotsByNo().get(decision.loserSlotNo());
        winnerSlot.updateScore(decision.winnerScore());
        winnerSlot.markWinner(true);
        loserSlot.updateScore(decision.loserScore());
        loserSlot.markWinner(false);
        context.match().finish(winnerSlot.getParticipantId());

        LocalDateTime reviewedAt = LocalDateTime.now();
        submission.approve(adminUserId, reviewedAt, null);
        rejectOtherPendingSubmissions(tournamentId, matchId, submissionId, adminUserId, reviewedAt);

        progressionService.propagateManualResult(
                context.match().getId(),
                context.stage().getId(),
                winnerSlot.getParticipantId(),
                loserSlot.getParticipantId()
        );

        return tournamentService.buildDetail(context.tournament());
    }

    @Transactional
    public TournamentScoreSubmissionResponseDto rejectSubmission(
            Long tournamentId,
            Long matchId,
            Long submissionId,
            TournamentScoreSubmissionRejectRequestDto request,
            Long adminUserId,
            String actorRole
    ) {
        requireAdmin(actorRole);
        MatchContext context = loadContext(tournamentId, matchId);
        if (!TournamentMatchEntity.STATUS_READY.equals(context.match().getStatus())) {
            throw invalid("Only READY matches can reject score submissions.");
        }
        TournamentMatchScoreSubmissionEntity submission = findSubmission(tournamentId, matchId, submissionId);
        if (!TournamentMatchScoreSubmissionEntity.STATUS_PENDING.equals(submission.getStatus())) {
            throw invalid("Only PENDING score submissions can be rejected.");
        }

        submission.reject(adminUserId, LocalDateTime.now(), request == null ? null : request.getAdminNote());
        return toResponse(submission, loadSubmitterLoginIds(List.of(submission)));
    }

    private MatchContext loadContext(Long tournamentId, Long matchId) {
        TournamentEntity tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> notFound("Tournament not found."));
        TournamentMatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> notFound("Match not found."));
        TournamentStageEntity stage = stageRepository.findById(match.getStageId())
                .orElseThrow(() -> notFound("Tournament stage not found."));
        if (!Objects.equals(stage.getTournamentId(), tournamentId)) {
            throw notFound("Match not found in tournament.");
        }

        List<TournamentMatchSlotEntity> slots = matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(matchId);
        Map<Integer, TournamentMatchSlotEntity> slotsByNo = slots.stream()
                .collect(Collectors.toMap(TournamentMatchSlotEntity::getSlotNo, Function.identity()));
        Map<Long, TournamentParticipantEntity> participantsById = loadParticipants(slots);
        Map<Integer, TournamentParticipantEntity> participantsBySlotNo = loadParticipantsBySlotNo(slots, participantsById, tournamentId);

        return new MatchContext(tournament, match, stage, slots, slotsByNo, participantsBySlotNo);
    }

    private Map<Long, TournamentParticipantEntity> loadParticipants(List<TournamentMatchSlotEntity> slots) {
        Set<Long> participantIds = slots.stream()
                .map(TournamentMatchSlotEntity::getParticipantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (participantIds.isEmpty()) {
            return Map.of();
        }

        return participantRepository.findAllById(participantIds).stream()
                .collect(Collectors.toMap(TournamentParticipantEntity::getId, Function.identity()));
    }

    private Map<Integer, TournamentParticipantEntity> loadParticipantsBySlotNo(
            List<TournamentMatchSlotEntity> slots,
            Map<Long, TournamentParticipantEntity> participantsById,
            Long tournamentId
    ) {
        Map<Integer, TournamentParticipantEntity> participantsBySlotNo = new HashMap<>();
        for (TournamentMatchSlotEntity slot : slots) {
            Long participantId = slot.getParticipantId();
            if (participantId == null) {
                continue;
            }
            TournamentParticipantEntity participant = participantsById.get(participantId);
            if (participant == null) {
                throw notFound("Tournament participant not found.");
            }
            if (!Objects.equals(participant.getTournamentId(), tournamentId)) {
                throw invalid("Match participant does not belong to tournament.");
            }
            participantsBySlotNo.put(slot.getSlotNo(), participant);
        }
        return participantsBySlotNo;
    }

    private void validateScoreSubmittableMatch(MatchContext context) {
        if (TournamentEntity.STATUS_FINISHED.equals(context.tournament().getStatus())) {
            throw invalid("Finished tournament cannot accept score submissions.");
        }
        if (!TournamentMatchEntity.STATUS_READY.equals(context.match().getStatus())) {
            throw invalid("Only READY matches can accept score submissions.");
        }
        if (context.slots().size() != 2
                || !context.slotsByNo().containsKey(1)
                || !context.slotsByNo().containsKey(2)) {
            throw invalid("Score submission requires slot 1 and slot 2.");
        }
        if (!isActualParticipantSlot(context.slotsByNo().get(1))
                || !isActualParticipantSlot(context.slotsByNo().get(2))) {
            throw invalid("BYE or empty slots cannot accept score submissions.");
        }
        if (!context.participantsBySlotNo().containsKey(1) || !context.participantsBySlotNo().containsKey(2)) {
            throw notFound("Tournament participant not found.");
        }
    }

    private Long resolveSubmitterParticipantId(MatchContext context, Long actorUserId, String actorRole) {
        requireAuthenticated(actorUserId);
        if (isAdmin(actorRole)) {
            return null;
        }
        return findParticipantIdForUser(context, actorUserId)
                .orElseThrow(() -> forbidden("Only match participants or administrators can submit scores."));
    }

    private void requireSubmissionViewer(MatchContext context, Long actorUserId, String actorRole) {
        requireAuthenticated(actorUserId);
        if (isAdmin(actorRole)) {
            return;
        }
        if (findParticipantIdForUser(context, actorUserId).isEmpty()) {
            throw forbidden("Only match participants or administrators can view score submissions.");
        }
    }

    private Optional<Long> findParticipantIdForUser(MatchContext context, Long actorUserId) {
        return context.participantsBySlotNo().values().stream()
                .filter(participant -> Objects.equals(participant.getUserId(), actorUserId))
                .map(TournamentParticipantEntity::getId)
                .findFirst();
    }

    private ScoreDecision decideScore(
            TournamentMatchEntity match,
            TournamentScoreSubmissionRequestDto request,
            Map<Integer, TournamentMatchSlotEntity> slotsByNo
    ) {
        Map<Integer, Integer> scoresBySlotNo = normalizeScores(request, slotsByNo);
        return decideScores(match.getBestOf(), scoresBySlotNo.get(1), scoresBySlotNo.get(2));
    }

    private ScoreDecision decideStoredScore(TournamentMatchEntity match, TournamentMatchScoreSubmissionEntity submission) {
        return decideScores(match.getBestOf(), submission.getSlot1Score(), submission.getSlot2Score());
    }

    private Map<Integer, Integer> normalizeScores(
            TournamentScoreSubmissionRequestDto request,
            Map<Integer, TournamentMatchSlotEntity> slotsByNo
    ) {
        if (request == null || request.getScores() == null || request.getScores().size() != 2) {
            throw invalid("Scores must include exactly two slots.");
        }

        Map<Integer, Integer> scoresBySlotNo = new HashMap<>();
        for (TournamentMatchScoreRequestDto scoreRequest : request.getScores()) {
            if (scoreRequest == null || scoreRequest.getSlotNo() == null || scoreRequest.getScore() == null) {
                throw invalid("Slot number and score are required.");
            }
            if (scoreRequest.getScore() < 0) {
                throw invalid("Score must be zero or greater.");
            }
            if (!slotsByNo.containsKey(scoreRequest.getSlotNo())) {
                throw invalid("Scores must match match slots.");
            }
            Integer previous = scoresBySlotNo.put(scoreRequest.getSlotNo(), scoreRequest.getScore());
            if (previous != null) {
                throw invalid("Duplicate slot score is not allowed.");
            }
        }
        if (!scoresBySlotNo.containsKey(1) || !scoresBySlotNo.containsKey(2)) {
            throw invalid("Scores must match match slots.");
        }
        return scoresBySlotNo;
    }

    private ScoreDecision decideScores(Integer bestOf, Integer slot1Score, Integer slot2Score) {
        int requiredWins = validateBestOf(bestOf) / 2 + 1;

        boolean slot1Wins = slot1Score == requiredWins && slot2Score < requiredWins;
        boolean slot2Wins = slot2Score == requiredWins && slot1Score < requiredWins;
        if (slot1Wins == slot2Wins) {
            throw invalid("Scores do not represent a completed best-of result.");
        }
        if (slot1Wins) {
            return new ScoreDecision(slot1Score, slot2Score, 1, 2, slot1Score, slot2Score);
        }
        return new ScoreDecision(slot1Score, slot2Score, 2, 1, slot2Score, slot1Score);
    }

    private int validateBestOf(Integer bestOf) {
        if (bestOf == null || bestOf <= 0 || bestOf % 2 == 0) {
            throw invalid("bestOf must be a positive odd number.");
        }
        return bestOf;
    }

    private void rejectOtherPendingSubmissions(
            Long tournamentId,
            Long matchId,
            Long approvedSubmissionId,
            Long adminUserId,
            LocalDateTime reviewedAt
    ) {
        submissionRepository.findAllByTournamentIdAndMatchIdAndStatus(
                        tournamentId,
                        matchId,
                        TournamentMatchScoreSubmissionEntity.STATUS_PENDING
                )
                .stream()
                .filter(submission -> !Objects.equals(submission.getId(), approvedSubmissionId))
                .forEach(submission -> submission.reject(adminUserId, reviewedAt, AUTO_REJECT_NOTE));
    }

    private TournamentMatchScoreSubmissionEntity findSubmission(Long tournamentId, Long matchId, Long submissionId) {
        return submissionRepository.findByIdAndTournamentIdAndMatchId(submissionId, tournamentId, matchId)
                .orElseThrow(() -> notFound("Score submission not found."));
    }

    private Map<Long, String> loadSubmitterLoginIds(List<TournamentMatchScoreSubmissionEntity> submissions) {
        Set<Long> userIds = submissions.stream()
                .map(TournamentMatchScoreSubmissionEntity::getSubmittedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .filter(user -> user.getUserId() != null && !user.getUserId().isBlank())
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUserId));
    }

    private TournamentScoreSubmissionResponseDto toResponse(
            TournamentMatchScoreSubmissionEntity submission,
            Map<Long, String> submitterLoginIds
    ) {
        return TournamentScoreSubmissionResponseDto.builder()
                .id(submission.getId())
                .submissionId(submission.getId())
                .tournamentId(submission.getTournamentId())
                .matchId(submission.getMatchId())
                .submittedByUserId(submission.getSubmittedByUserId())
                .submittedByParticipantId(submission.getSubmittedByParticipantId())
                .submitterLoginId(submitterLoginIds.get(submission.getSubmittedByUserId()))
                .submitterRole(submission.getSubmitterRole())
                .slot1Score(submission.getSlot1Score())
                .slot2Score(submission.getSlot2Score())
                .winnerSlotNo(submission.getWinnerSlotNo())
                .status(submission.getStatus())
                .adminReviewerUserId(submission.getAdminReviewerUserId())
                .adminReviewedAt(submission.getAdminReviewedAt())
                .adminNote(submission.getAdminNote())
                .regDate(submission.getRegDate())
                .updateDate(submission.getUpdateDate())
                .build();
    }

    private boolean isActualParticipantSlot(TournamentMatchSlotEntity slot) {
        return slot != null && slot.getParticipantId() != null && !Integer.valueOf(1).equals(slot.getIsBye());
    }

    private boolean isAdmin(String actorRole) {
        return ADMIN_ROLES.contains(actorRole);
    }

    private void requireAdmin(String actorRole) {
        if (!isAdmin(actorRole)) {
            throw forbidden("Administrator role is required.");
        }
    }

    private void requireAuthenticated(Long actorUserId) {
        if (actorUserId == null) {
            throw forbidden("Authentication is required.");
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private NoSuchElementException notFound(String message) {
        return new NoSuchElementException(message);
    }

    private AccessDeniedException forbidden(String message) {
        return new AccessDeniedException(message);
    }

    private record MatchContext(
            TournamentEntity tournament,
            TournamentMatchEntity match,
            TournamentStageEntity stage,
            List<TournamentMatchSlotEntity> slots,
            Map<Integer, TournamentMatchSlotEntity> slotsByNo,
            Map<Integer, TournamentParticipantEntity> participantsBySlotNo
    ) {
    }

    private record ScoreDecision(
            Integer slot1Score,
            Integer slot2Score,
            Integer winnerSlotNo,
            Integer loserSlotNo,
            Integer winnerScore,
            Integer loserScore
    ) {
    }
}
