package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentMatchScoreRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionRejectRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionSetRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionSetResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchScoreSubmissionEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchScoreSubmissionSetEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSetEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentGroupEntryRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionSetRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSetRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentResultSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TournamentMatchScoreSubmissionService {

    private static final Set<String> ADMIN_ROLES = Set.of("ROLE_MANAGER", "ROLE_MASTER", "ROLE_ADMIN");
    private static final String AUTO_REJECT_NOTE = "Another score submission was approved.";
    private static final String RACE_TERRAN = "TERRAN";
    private static final String RACE_ZERG = "ZERG";
    private static final String RACE_PROTOSS = "PROTOSS";
    private static final String MATCHES_GROUP_CODE = "MATCHES";
    private static final String RACE_SURVIVAL_EMPTY_SLOT_LABEL = "선수 지정";
    private static final List<String> RACE_ORDER = List.of(RACE_TERRAN, RACE_ZERG, RACE_PROTOSS);

    private final TournamentRepository tournamentRepository;
    private final TournamentGroupRepository groupRepository;
    private final TournamentGroupEntryRepository groupEntryRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentStageRepository stageRepository;
    private final TournamentMatchSlotRepository matchSlotRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentResultSlotRepository resultSlotRepository;
    private final TournamentMatchScoreSubmissionRepository submissionRepository;
    private final TournamentMatchScoreSubmissionSetRepository submissionSetRepository;
    private final TournamentMatchSetRepository matchSetRepository;
    private final UserRepository userRepository;
    private final TournamentBracketProgressionService progressionService;
    private final TournamentService tournamentService;
    private final MapRepository mapRepository;

    @Transactional
    public TournamentScoreSubmissionResponseDto submitScore(
            Long tournamentId,
            Long matchId,
            TournamentScoreSubmissionRequestDto request,
            Long actorUserId,
            String actorRole
    ) {
        log.info(
                "[tournament-score] submit start tournamentId={} matchId={} actorUserId={} actorRole={} requestBestOf={} requestSetCount={} requestScoreCount={} requestMapId={}",
                tournamentId,
                matchId,
                actorUserId,
                actorRole,
                request == null ? null : request.getBestOf(),
                request == null || request.getSets() == null ? 0 : request.getSets().size(),
                request == null || request.getScores() == null ? 0 : request.getScores().size(),
                request == null ? null : request.getMapId()
        );
        MatchContext context = loadContext(tournamentId, matchId);
        validateScoreSubmittableMatch(context);
        Long submittedByParticipantId = resolveSubmitterParticipantId(context, actorUserId, actorRole);
        String submitterRole = isAdmin(actorRole)
                ? TournamentMatchScoreSubmissionEntity.ROLE_ADMIN
                : TournamentMatchScoreSubmissionEntity.ROLE_PLAYER;
        SubmissionDecision decision = decideSubmission(context, request);
        log.info(
                "[tournament-score] submit validated tournamentId={} matchId={} tournamentStatus={} matchStatus={} stageType={} decisionBestOf={} decisionScore={}:{} winnerSlotNo={} mapId={} setCount={}",
                tournamentId,
                matchId,
                context.tournament().getStatus(),
                context.match().getStatus(),
                context.stage().getStageType(),
                decision.bestOf(),
                decision.slot1Score(),
                decision.slot2Score(),
                decision.winnerSlotNo(),
                decision.mapId(),
                decision.sets().size()
        );

        TournamentMatchScoreSubmissionEntity submission = TournamentMatchScoreSubmissionEntity.builder()
                .tournamentId(tournamentId)
                .matchId(matchId)
                .submittedByUserId(actorUserId)
                .submittedByParticipantId(submittedByParticipantId)
                .submitterRole(submitterRole)
                .bestOf(decision.bestOf())
                .slot1Score(decision.slot1Score())
                .slot2Score(decision.slot2Score())
                .winnerSlotNo(decision.winnerSlotNo())
                .mapId(decision.mapId())
                .status(TournamentMatchScoreSubmissionEntity.STATUS_PENDING)
                .build();

        TournamentMatchScoreSubmissionEntity savedSubmission = submissionRepository.save(submission);
        saveSubmissionSets(savedSubmission.getId(), decision.sets());
        log.info(
                "[tournament-score] submit saved tournamentId={} matchId={} submissionId={} submissionStatus={} score={}:{} winnerSlotNo={} mapId={}",
                tournamentId,
                matchId,
                savedSubmission.getId(),
                savedSubmission.getStatus(),
                savedSubmission.getSlot1Score(),
                savedSubmission.getSlot2Score(),
                savedSubmission.getWinnerSlotNo(),
                savedSubmission.getMapId()
        );
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

        List<TournamentMatchScoreSubmissionEntity> submissions = isAdmin(actorRole)
                ? submissionRepository.findAllByTournamentIdAndMatchIdOrderByRegDateDescIdDesc(tournamentId, matchId)
                : submissionRepository.findAllByTournamentIdAndMatchIdAndSubmittedByUserIdOrderByRegDateDescIdDesc(
                        tournamentId,
                        matchId,
                        actorUserId
                );
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

        TournamentMatchScoreSubmissionEntity submission = findSubmission(tournamentId, matchId, submissionId);
        log.info(
                "[tournament-score] approve start tournamentId={} matchId={} submissionId={} actorUserId={} actorRole={} tournamentStatus={} matchStatus={} matchWinnerParticipantId={} submissionStatus={} submissionScore={}:{} submissionWinnerSlotNo={} submissionBestOf={} submissionMapId={}",
                tournamentId,
                matchId,
                submissionId,
                adminUserId,
                actorRole,
                context.tournament().getStatus(),
                context.match().getStatus(),
                context.match().getWinnerParticipantId(),
                submission.getStatus(),
                submission.getSlot1Score(),
                submission.getSlot2Score(),
                submission.getWinnerSlotNo(),
                submission.getBestOf(),
                submission.getMapId()
        );
        if (!TournamentMatchScoreSubmissionEntity.STATUS_PENDING.equals(submission.getStatus())) {
            throw invalid("Only PENDING score submissions can be approved.");
        }
        validateScoreApprovableMatch(context);
        requireSubmissionMap(submission);

        SubmissionDecision decision = decideStoredSubmission(context, submission);
        if (!Objects.equals(submission.getWinnerSlotNo(), decision.winnerSlotNo())) {
            throw invalid("Stored winner slot does not match submission scores.");
        }

        context.match().updateBestOf(decision.bestOf());
        context.match().assignMap(submission.getMapId());
        replaceOfficialMatchSets(context.match().getId(), decision.sets());
        TournamentMatchSlotEntity winnerSlot = context.slotsByNo().get(decision.winnerSlotNo());
        TournamentMatchSlotEntity loserSlot = context.slotsByNo().get(decision.loserSlotNo());
        winnerSlot.updateScore(decision.winnerScore());
        winnerSlot.markWinner(true);
        loserSlot.updateScore(decision.loserScore());
        loserSlot.markWinner(false);
        context.match().finish(winnerSlot.getParticipantId());
        matchRepository.save(context.match());
        log.info(
                "[tournament-score] approve applying tournamentId={} matchId={} submissionId={} decisionBestOf={} decisionScore={}:{} winnerSlotNo={} winnerParticipantId={} loserParticipantId={} mapId={} setCount={}",
                tournamentId,
                matchId,
                submissionId,
                decision.bestOf(),
                decision.slot1Score(),
                decision.slot2Score(),
                decision.winnerSlotNo(),
                winnerSlot.getParticipantId(),
                loserSlot.getParticipantId(),
                submission.getMapId(),
                decision.sets().size()
        );

        LocalDateTime reviewedAt = LocalDateTime.now();
        submission.approve(adminUserId, reviewedAt, null);
        rejectOtherPendingSubmissions(tournamentId, matchId, submissionId, adminUserId, reviewedAt);
        log.info(
                "[tournament-score] approve saved tournamentId={} matchId={} submissionId={} matchStatus={} matchWinnerParticipantId={} submissionStatus={} reviewedAt={}",
                tournamentId,
                matchId,
                submissionId,
                context.match().getStatus(),
                context.match().getWinnerParticipantId(),
                submission.getStatus(),
                reviewedAt
        );

        if (TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(context.stage().getStageType())) {
            progressRaceSurvival(context, winnerSlot.getParticipantId(), loserSlot.getParticipantId());
        } else {
            progressionService.propagateManualResult(
                    context.match().getId(),
                    context.stage().getId(),
                    winnerSlot.getParticipantId(),
                    loserSlot.getParticipantId()
            );
        }

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
        TournamentMatchScoreSubmissionEntity submission = findSubmission(tournamentId, matchId, submissionId);
        log.info(
                "[tournament-score] reject start tournamentId={} matchId={} submissionId={} actorUserId={} actorRole={} tournamentStatus={} matchStatus={} submissionStatus={} notePresent={}",
                tournamentId,
                matchId,
                submissionId,
                adminUserId,
                actorRole,
                context.tournament().getStatus(),
                context.match().getStatus(),
                submission.getStatus(),
                request != null && request.getAdminNote() != null && !request.getAdminNote().isBlank()
        );
        if (!TournamentMatchScoreSubmissionEntity.STATUS_PENDING.equals(submission.getStatus())) {
            throw invalid("Only PENDING score submissions can be rejected.");
        }

        submission.reject(adminUserId, LocalDateTime.now(), request == null ? null : request.getAdminNote());
        log.info(
                "[tournament-score] reject saved tournamentId={} matchId={} submissionId={} submissionStatus={}",
                tournamentId,
                matchId,
                submissionId,
                submission.getStatus()
        );
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
        validateScoreMatchSlots(context);
    }

    private void validateScoreApprovableMatch(MatchContext context) {
        if (TournamentEntity.STATUS_FINISHED.equals(context.tournament().getStatus())) {
            throw invalid("Finished tournament cannot approve score submissions.");
        }
        boolean readyMatch = TournamentMatchEntity.STATUS_READY.equals(context.match().getStatus());
        boolean finishedMatch = TournamentMatchEntity.STATUS_FINISHED.equals(context.match().getStatus());
        if (TournamentMatchEntity.STATUS_CANCELLED.equals(context.match().getStatus())) {
            throw invalid("Cancelled match cannot approve score submissions.");
        }
        if (!readyMatch && !finishedMatch) {
            throw invalid("Only READY matches can approve score submissions.");
        }
        if (finishedMatch && hasApprovedScoreSubmission(context)) {
            throw invalid("Finished match result is already fixed.");
        }
        validateScoreMatchSlots(context);
    }

    private void validateScoreMatchSlots(MatchContext context) {
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

    private boolean hasApprovedScoreSubmission(MatchContext context) {
        return submissionRepository.existsByTournamentIdAndMatchIdAndStatus(
                context.tournament().getId(),
                context.match().getId(),
                TournamentMatchScoreSubmissionEntity.STATUS_APPROVED
        );
    }

    private Long resolveSubmissionMap(MatchContext context, TournamentScoreSubmissionRequestDto request) {
        Long requestedMapId = request == null ? null : request.getMapId();
        Long currentMapId = context.match().getMapId();
        Long effectiveMapId = requestedMapId == null ? currentMapId : requestedMapId;

        if (effectiveMapId == null) {
            throw invalid("Map is required before submitting a score.");
        }
        if (!mapRepository.existsById(effectiveMapId)) {
            throw invalid("Unknown mapId in score submission.");
        }

        if (
                !Objects.equals(currentMapId, effectiveMapId)
                        && hasLockedScoreSubmission(context.tournament().getId(), context.match().getId())
        ) {
            throw invalid("Match map cannot be changed after score submission.");
        }

        return effectiveMapId;
    }

    private void requireSubmissionMap(TournamentMatchScoreSubmissionEntity submission) {
        Long mapId = submission.getMapId();
        if (mapId == null) {
            throw invalid("Map is required before approving a score submission.");
        }
        if (!mapRepository.existsById(mapId)) {
            throw invalid("Unknown mapId in score submission.");
        }
    }

    private boolean hasLockedScoreSubmission(Long tournamentId, Long matchId) {
        return submissionRepository.existsByTournamentIdAndMatchIdAndStatusNot(
                tournamentId,
                matchId,
                TournamentMatchScoreSubmissionEntity.STATUS_REJECTED
        );
    }

    private Long resolveSubmitterParticipantId(MatchContext context, Long actorUserId, String actorRole) {
        requireAuthenticated(actorUserId);
        if (isAdmin(actorRole)) {
            return null;
        }
        if (isRaceSurvival(context)) {
            return findTournamentParticipantIdForUser(context.tournament().getId(), actorUserId)
                    .orElseThrow(() -> forbidden("Only tournament participants or administrators can submit scores."));
        }
        return findMatchParticipantIdForUser(context, actorUserId)
                .orElseThrow(() -> forbidden("Only match participants or administrators can submit scores."));
    }

    private void requireSubmissionViewer(MatchContext context, Long actorUserId, String actorRole) {
        requireAuthenticated(actorUserId);
        if (isAdmin(actorRole)) {
            return;
        }
        if (isRaceSurvival(context)) {
            if (findTournamentParticipantIdForUser(context.tournament().getId(), actorUserId).isPresent()) {
                return;
            }
            throw forbidden("Only tournament participants or administrators can view score submissions.");
        }
        if (findMatchParticipantIdForUser(context, actorUserId).isEmpty()) {
            throw forbidden("Only match participants or administrators can view score submissions.");
        }
    }

    private Optional<Long> findMatchParticipantIdForUser(MatchContext context, Long actorUserId) {
        return context.participantsBySlotNo().values().stream()
                .filter(participant -> Objects.equals(participant.getUserId(), actorUserId))
                .map(TournamentParticipantEntity::getId)
                .findFirst();
    }

    private Optional<Long> findTournamentParticipantIdForUser(Long tournamentId, Long actorUserId) {
        if (actorUserId == null) {
            return Optional.empty();
        }
        return participantRepository.findFirstByTournamentIdAndUserIdOrderBySeedNoAscIdAsc(tournamentId, actorUserId)
                .map(TournamentParticipantEntity::getId);
    }

    private boolean isRaceSurvival(MatchContext context) {
        return TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(context.stage().getStageType());
    }

    private boolean usesSetBasedScore(MatchContext context) {
        return TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(context.stage().getStageType())
                || TournamentStageEntity.TYPE_DUAL_GROUP.equals(context.stage().getStageType())
                || TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(context.stage().getStageType());
    }

    private SubmissionDecision decideSubmission(MatchContext context, TournamentScoreSubmissionRequestDto request) {
        if (usesSetBasedScore(context)) {
            return decideSetSubmission(context, request);
        }

        Long submissionMapId = resolveSubmissionMap(context, request);
        ScoreDecision score = decideScore(context, request);
        int bestOf = validateBestOf(context.match().getBestOf());
        return new SubmissionDecision(
                bestOf,
                score.slot1Score(),
                score.slot2Score(),
                score.winnerSlotNo(),
                score.loserSlotNo(),
                score.winnerScore(),
                score.loserScore(),
                submissionMapId,
                List.of()
        );
    }

    private SubmissionDecision decideStoredSubmission(
            MatchContext context,
            TournamentMatchScoreSubmissionEntity submission
    ) {
        List<TournamentMatchScoreSubmissionSetEntity> storedSets =
                submissionSetRepository.findAllByScoreSubmissionIdOrderBySetNoAsc(submission.getId());
        if (!storedSets.isEmpty()) {
            return decideStoredSetSubmission(context, submission, storedSets);
        }

        int bestOf = validateBestOf(submission.getBestOf() == null ? context.match().getBestOf() : submission.getBestOf());
        ScoreDecision score = decideScores(
                context.stage().getStageType(),
                bestOf,
                submission.getSlot1Score(),
                submission.getSlot2Score()
        );
        return new SubmissionDecision(
                bestOf,
                score.slot1Score(),
                score.slot2Score(),
                score.winnerSlotNo(),
                score.loserSlotNo(),
                score.winnerScore(),
                score.loserScore(),
                submission.getMapId(),
                List.of()
        );
    }

    private SubmissionDecision decideSetSubmission(MatchContext context, TournamentScoreSubmissionRequestDto request) {
        if (request == null || request.getBestOf() == null) {
            throw invalid("bestOf is required before submitting a score.");
        }
        int bestOf = validateBestOf(request.getBestOf());
        boolean ultimateBattle = TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(context.stage().getStageType());
        List<ValidatedSubmissionSet> sets = ultimateBattle
                ? normalizeUltimateBattleSubmissionSets(bestOf, request.getSets())
                : normalizeSubmissionSets(bestOf, request.getSets());
        ScoreDecision score = ultimateBattle
                ? decideUltimateBattleSetScores(bestOf, sets)
                : decideSetScores(bestOf, sets);
        Long mapId = sets.get(0).mapId();
        return new SubmissionDecision(
                bestOf,
                score.slot1Score(),
                score.slot2Score(),
                score.winnerSlotNo(),
                score.loserSlotNo(),
                score.winnerScore(),
                score.loserScore(),
                mapId,
                sets
        );
    }

    private SubmissionDecision decideStoredSetSubmission(
            MatchContext context,
            TournamentMatchScoreSubmissionEntity submission,
            List<TournamentMatchScoreSubmissionSetEntity> storedSets
    ) {
        if (submission.getBestOf() == null) {
            throw invalid("Stored bestOf is missing.");
        }
        int bestOf = validateBestOf(submission.getBestOf());
        List<ValidatedSubmissionSet> sets = storedSets.stream()
                .map(set -> {
                    if (set.getWinnerSlotNo() == null || (set.getWinnerSlotNo() != 1 && set.getWinnerSlotNo() != 2)) {
                        throw invalid("Stored winner slot is invalid.");
                    }
                    if (set.getMapId() == null || !mapRepository.existsById(set.getMapId())) {
                        throw invalid("Unknown mapId in score submission.");
                    }
                    return new ValidatedSubmissionSet(set.getSetNo(), set.getWinnerSlotNo(), set.getMapId());
                })
                .toList();
        boolean ultimateBattle = TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(context.stage().getStageType());
        ScoreDecision score = ultimateBattle
                ? decideUltimateBattleSetScores(bestOf, sets)
                : decideSetScores(bestOf, sets);
        return new SubmissionDecision(
                bestOf,
                score.slot1Score(),
                score.slot2Score(),
                score.winnerSlotNo(),
                score.loserSlotNo(),
                score.winnerScore(),
                score.loserScore(),
                sets.get(0).mapId(),
                sets
        );
    }

    private List<ValidatedSubmissionSet> normalizeSubmissionSets(
            int bestOf,
            List<TournamentScoreSubmissionSetRequestDto> requestedSets
    ) {
        if (requestedSets == null || requestedSets.isEmpty()) {
            throw invalid("Score submission sets are required.");
        }

        int requiredWins = bestOf / 2 + 1;
        Map<Integer, TournamentScoreSubmissionSetRequestDto> setsByNo = new HashMap<>();
        for (TournamentScoreSubmissionSetRequestDto requestedSet : requestedSets) {
            if (requestedSet == null || requestedSet.getSetNo() == null) {
                throw invalid("setNo is required.");
            }
            Integer setNo = requestedSet.getSetNo();
            if (setNo < 1 || setNo > bestOf) {
                throw invalid("setNo must be between 1 and bestOf.");
            }
            if (setsByNo.put(setNo, requestedSet) != null) {
                throw invalid("Duplicate setNo is not allowed.");
            }
        }

        List<ValidatedSubmissionSet> playedSets = new ArrayList<>();
        int slot1Wins = 0;
        int slot2Wins = 0;
        for (int setNo = 1; setNo <= bestOf; setNo++) {
            TournamentScoreSubmissionSetRequestDto requestedSet = setsByNo.get(setNo);
            if (requestedSet == null) {
                break;
            }

            Integer winnerSlotNo = requestedSet.getWinnerSlotNo();
            if (winnerSlotNo == null || (winnerSlotNo != 1 && winnerSlotNo != 2)) {
                throw invalid("winnerSlotNo must be 1 or 2.");
            }
            Long mapId = requestedSet.getMapId();
            if (mapId == null) {
                throw invalid("Map is required for every played set.");
            }
            if (!mapRepository.existsById(mapId)) {
                throw invalid("Unknown mapId in score submission.");
            }

            playedSets.add(new ValidatedSubmissionSet(setNo, winnerSlotNo, mapId));
            if (winnerSlotNo == 1) {
                slot1Wins++;
            } else {
                slot2Wins++;
            }
            if (slot1Wins == requiredWins || slot2Wins == requiredWins) {
                return playedSets;
            }
        }

        throw invalid("Set results do not represent a completed best-of result.");
    }

    private List<ValidatedSubmissionSet> normalizeUltimateBattleSubmissionSets(
            int bestOf,
            List<TournamentScoreSubmissionSetRequestDto> requestedSets
    ) {
        if (requestedSets == null || requestedSets.size() != bestOf) {
            throw invalid("ULTIMATE_BATTLE requires one set result for every game.");
        }

        Map<Integer, TournamentScoreSubmissionSetRequestDto> setsByNo = new HashMap<>();
        for (TournamentScoreSubmissionSetRequestDto requestedSet : requestedSets) {
            if (requestedSet == null || requestedSet.getSetNo() == null) {
                throw invalid("setNo is required.");
            }
            Integer setNo = requestedSet.getSetNo();
            if (setNo < 1 || setNo > bestOf) {
                throw invalid("setNo must be between 1 and bestOf.");
            }
            if (setsByNo.put(setNo, requestedSet) != null) {
                throw invalid("Duplicate setNo is not allowed.");
            }
        }

        List<ValidatedSubmissionSet> playedSets = new ArrayList<>();
        for (int setNo = 1; setNo <= bestOf; setNo++) {
            TournamentScoreSubmissionSetRequestDto requestedSet = setsByNo.get(setNo);
            if (requestedSet == null) {
                throw invalid("ULTIMATE_BATTLE requires consecutive set results.");
            }

            Integer winnerSlotNo = requestedSet.getWinnerSlotNo();
            if (winnerSlotNo == null || (winnerSlotNo != 1 && winnerSlotNo != 2)) {
                throw invalid("winnerSlotNo must be 1 or 2.");
            }
            Long mapId = requestedSet.getMapId();
            if (mapId == null) {
                throw invalid("Map is required for every played set.");
            }
            if (!mapRepository.existsById(mapId)) {
                throw invalid("Unknown mapId in score submission.");
            }

            playedSets.add(new ValidatedSubmissionSet(setNo, winnerSlotNo, mapId));
        }

        return playedSets;
    }

    private ScoreDecision decideSetScores(int bestOf, List<ValidatedSubmissionSet> sets) {
        int requiredWins = bestOf / 2 + 1;
        int slot1Score = 0;
        int slot2Score = 0;
        for (ValidatedSubmissionSet set : sets) {
            if (set.winnerSlotNo() == 1) {
                slot1Score++;
            } else {
                slot2Score++;
            }
        }

        boolean slot1Wins = slot1Score == requiredWins && slot2Score < requiredWins;
        boolean slot2Wins = slot2Score == requiredWins && slot1Score < requiredWins;
        if (slot1Wins == slot2Wins) {
            throw invalid("Set results do not represent a completed best-of result.");
        }
        if (slot1Wins) {
            return new ScoreDecision(slot1Score, slot2Score, 1, 2, slot1Score, slot2Score);
        }
        return new ScoreDecision(slot1Score, slot2Score, 2, 1, slot2Score, slot1Score);
    }

    private ScoreDecision decideUltimateBattleSetScores(int bestOf, List<ValidatedSubmissionSet> sets) {
        if (sets.size() != bestOf) {
            throw invalid("ULTIMATE_BATTLE requires one set result for every game.");
        }

        int slot1Score = 0;
        int slot2Score = 0;
        for (ValidatedSubmissionSet set : sets) {
            if (set.winnerSlotNo() == 1) {
                slot1Score++;
            } else {
                slot2Score++;
            }
        }

        if (slot1Score == slot2Score) {
            throw invalid("ULTIMATE_BATTLE scores cannot be tied.");
        }
        if (slot1Score > slot2Score) {
            return new ScoreDecision(slot1Score, slot2Score, 1, 2, slot1Score, slot2Score);
        }
        return new ScoreDecision(slot1Score, slot2Score, 2, 1, slot2Score, slot1Score);
    }

    private void saveSubmissionSets(Long submissionId, List<ValidatedSubmissionSet> sets) {
        if (submissionId == null || sets.isEmpty()) {
            return;
        }
        submissionSetRepository.saveAll(sets.stream()
                .map(set -> TournamentMatchScoreSubmissionSetEntity.builder()
                        .scoreSubmissionId(submissionId)
                        .setNo(set.setNo())
                        .winnerSlotNo(set.winnerSlotNo())
                        .mapId(set.mapId())
                        .build())
                .toList());
    }

    private void replaceOfficialMatchSets(Long matchId, List<ValidatedSubmissionSet> sets) {
        if (sets.isEmpty()) {
            return;
        }
        matchSetRepository.deleteByMatchId(matchId);
        matchSetRepository.saveAll(sets.stream()
                .map(set -> TournamentMatchSetEntity.builder()
                        .matchId(matchId)
                        .setNo(set.setNo())
                        .winnerSlotNo(set.winnerSlotNo())
                        .mapId(set.mapId())
                        .build())
                .toList());
    }

    private ScoreDecision decideScore(MatchContext context, TournamentScoreSubmissionRequestDto request) {
        Map<Integer, Integer> scoresBySlotNo = normalizeScores(request, context.slotsByNo());
        return decideScores(
                context.stage().getStageType(),
                context.match().getBestOf(),
                scoresBySlotNo.get(1),
                scoresBySlotNo.get(2)
        );
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

    private ScoreDecision decideScores(String stageType, Integer bestOf, Integer slot1Score, Integer slot2Score) {
        if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(stageType)) {
            return decideUltimateBattleScores(bestOf, slot1Score, slot2Score);
        }
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

    private ScoreDecision decideUltimateBattleScores(Integer bestOf, Integer slot1Score, Integer slot2Score) {
        int totalGames = validateBestOf(bestOf);
        if (slot1Score + slot2Score != totalGames) {
            throw invalid("ULTIMATE_BATTLE scores must add up to bestOf.");
        }
        if (Objects.equals(slot1Score, slot2Score)) {
            throw invalid("ULTIMATE_BATTLE scores cannot be tied.");
        }
        if (slot1Score > slot2Score) {
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

    private void progressRaceSurvival(MatchContext context, Long winnerParticipantId, Long loserParticipantId) {
        TournamentParticipantEntity loser = participantRepository.findById(loserParticipantId)
                .orElseThrow(() -> notFound("Loser participant not found."));
        loser.updateStatus(TournamentParticipantEntity.STATUS_DROPPED);

        RaceSurvivalState state = loadRaceSurvivalState(context.stage().getId());
        String winnerRace = state.raceByParticipantId().get(winnerParticipantId);
        String loserRace = state.raceByParticipantId().get(loserParticipantId);
        if (winnerRace == null || loserRace == null) {
            throw invalid("RACE_SURVIVAL participant group is invalid.");
        }

        Set<String> aliveRaces = state.aliveRaces();
        if (aliveRaces.size() <= 1) {
            finishRaceSurvival(context, winnerParticipantId);
            return;
        }

        List<TournamentParticipantEntity> eligibleOpponents = state.aliveOpponents(winnerRace, winnerParticipantId);
        if (eligibleOpponents.isEmpty()) {
            finishRaceSurvival(context, winnerParticipantId);
            return;
        }

        Long opponentParticipantId = eligibleOpponents.size() == 1 ? eligibleOpponents.get(0).getId() : null;
        createNextRaceSurvivalMatch(context, winnerParticipantId, opponentParticipantId);
    }

    private RaceSurvivalState loadRaceSurvivalState(Long stageId) {
        List<TournamentGroupEntity> raceGroups = groupRepository.findAllByStageIdOrderByDisplayOrderAsc(stageId)
                .stream()
                .filter(group -> RACE_ORDER.contains(group.getGroupCode()))
                .toList();
        if (raceGroups.size() != RACE_ORDER.size()) {
            throw invalid("RACE_SURVIVAL requires TERRAN, ZERG, and PROTOSS groups.");
        }

        Map<Long, String> raceByParticipantId = new HashMap<>();
        Map<String, List<TournamentParticipantEntity>> participantsByRace = new HashMap<>();
        for (TournamentGroupEntity group : raceGroups) {
            List<TournamentGroupEntryEntity> entries = groupEntryRepository.findAllByGroupIdOrderByGroupSeedNoAsc(group.getId());
            Map<Long, TournamentParticipantEntity> participantsById = participantRepository
                    .findAllById(entries.stream().map(TournamentGroupEntryEntity::getParticipantId).toList())
                    .stream()
                    .collect(Collectors.toMap(TournamentParticipantEntity::getId, Function.identity()));
            List<TournamentParticipantEntity> participants = new ArrayList<>();
            for (TournamentGroupEntryEntity entry : entries) {
                TournamentParticipantEntity participant = participantsById.get(entry.getParticipantId());
                if (participant != null) {
                    participants.add(participant);
                    raceByParticipantId.put(participant.getId(), group.getGroupCode());
                }
            }
            participantsByRace.put(group.getGroupCode(), participants);
        }
        return new RaceSurvivalState(raceByParticipantId, participantsByRace);
    }

    private void createNextRaceSurvivalMatch(MatchContext context, Long winnerParticipantId, Long opponentParticipantId) {
        TournamentGroupEntity matchesGroup = groupRepository.findByStageIdAndGroupCode(
                        context.stage().getId(),
                        MATCHES_GROUP_CODE
                )
                .orElseThrow(() -> notFound("RACE_SURVIVAL matches group not found."));
        int matchNo = matchRepository.findAllByStageIdOrderByDisplayOrderAsc(context.stage().getId()).size() + 1;
        TournamentMatchEntity nextMatch = matchRepository.save(TournamentMatchEntity.builder()
                .stageId(context.stage().getId())
                .groupId(matchesGroup.getId())
                .matchKey("M" + matchNo)
                .matchRole(TournamentMatchEntity.ROLE_ROUND)
                .roundNo(matchNo)
                .matchNo(matchNo)
                .displayName("Match " + matchNo)
                .bestOf(1)
                .status(opponentParticipantId == null ? TournamentMatchEntity.STATUS_PENDING : TournamentMatchEntity.STATUS_READY)
                .layoutCol(1)
                .layoutRow(matchNo)
                .displayOrder(matchNo)
                .build());
        matchSlotRepository.save(TournamentMatchSlotEntity.builder()
                .matchId(nextMatch.getId())
                .slotNo(1)
                .participantId(winnerParticipantId)
                .isWinner(0)
                .isBye(0)
                .build());
        if (opponentParticipantId == null) {
            matchSlotRepository.save(TournamentMatchSlotEntity.builder()
                    .matchId(nextMatch.getId())
                    .slotNo(2)
                    .placeholderLabel(RACE_SURVIVAL_EMPTY_SLOT_LABEL)
                    .isWinner(0)
                    .isBye(0)
                    .build());
        } else {
            matchSlotRepository.save(TournamentMatchSlotEntity.builder()
                    .matchId(nextMatch.getId())
                    .slotNo(2)
                    .participantId(opponentParticipantId)
                    .isWinner(0)
                    .isBye(0)
                    .build());
        }
    }

    private void finishRaceSurvival(MatchContext context, Long winnerParticipantId) {
        resultSlotRepository.findByStageIdAndResultKey(context.stage().getId(), "CHAMPION")
                .ifPresent(resultSlot -> resultSlot.decide(winnerParticipantId, LocalDateTime.now()));
        context.tournament().finish();
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
        List<TournamentMatchScoreSubmissionSetEntity> sets = submission.getId() == null
                ? List.of()
                : submissionSetRepository.findAllByScoreSubmissionIdOrderBySetNoAsc(submission.getId());
        Map<Long, String> mapNamesById = loadSetMapNames(sets);
        return TournamentScoreSubmissionResponseDto.builder()
                .id(submission.getId())
                .submissionId(submission.getId())
                .tournamentId(submission.getTournamentId())
                .matchId(submission.getMatchId())
                .submittedByUserId(submission.getSubmittedByUserId())
                .submittedByParticipantId(submission.getSubmittedByParticipantId())
                .submitterLoginId(submitterLoginIds.get(submission.getSubmittedByUserId()))
                .submitterRole(submission.getSubmitterRole())
                .bestOf(submission.getBestOf())
                .slot1Score(submission.getSlot1Score())
                .slot2Score(submission.getSlot2Score())
                .winnerSlotNo(submission.getWinnerSlotNo())
                .mapId(submission.getMapId())
                .sets(sets.stream()
                        .map(set -> TournamentScoreSubmissionSetResponseDto.builder()
                                .setNo(set.getSetNo())
                                .winnerSlotNo(set.getWinnerSlotNo())
                                .mapId(set.getMapId())
                                .mapName(mapNamesById.get(set.getMapId()))
                                .build())
                        .toList())
                .status(submission.getStatus())
                .adminReviewerUserId(submission.getAdminReviewerUserId())
                .adminReviewedAt(submission.getAdminReviewedAt())
                .adminNote(submission.getAdminNote())
                .regDate(submission.getRegDate())
                .updateDate(submission.getUpdateDate())
                .build();
    }

    private Map<Long, String> loadSetMapNames(List<TournamentMatchScoreSubmissionSetEntity> sets) {
        Set<Long> mapIds = sets.stream()
                .map(TournamentMatchScoreSubmissionSetEntity::getMapId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (mapIds.isEmpty()) {
            return Map.of();
        }
        return mapRepository.findAllById(mapIds).stream()
                .collect(Collectors.toMap(MapEntity::getId, MapEntity::getMapName));
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

    private record SubmissionDecision(
            Integer bestOf,
            Integer slot1Score,
            Integer slot2Score,
            Integer winnerSlotNo,
            Integer loserSlotNo,
            Integer winnerScore,
            Integer loserScore,
            Long mapId,
            List<ValidatedSubmissionSet> sets
    ) {
    }

    private record ValidatedSubmissionSet(
            Integer setNo,
            Integer winnerSlotNo,
            Long mapId
    ) {
    }

    private record RaceSurvivalState(
            Map<Long, String> raceByParticipantId,
            Map<String, List<TournamentParticipantEntity>> participantsByRace
    ) {
        Set<String> aliveRaces() {
            return participantsByRace.entrySet().stream()
                    .filter(entry -> entry.getValue().stream().anyMatch(RaceSurvivalState::isAlive))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        List<TournamentParticipantEntity> aliveOpponents(String winnerRace, Long winnerParticipantId) {
            return participantsByRace.entrySet().stream()
                    .filter(entry -> !Objects.equals(entry.getKey(), winnerRace))
                    .flatMap(entry -> entry.getValue().stream())
                    .filter(RaceSurvivalState::isAlive)
                    .filter(participant -> !Objects.equals(participant.getId(), winnerParticipantId))
                    .toList();
        }

        private static boolean isAlive(TournamentParticipantEntity participant) {
            return !TournamentParticipantEntity.STATUS_DROPPED.equals(participant.getStatus());
        }
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
