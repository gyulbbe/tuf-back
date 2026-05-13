package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentRouteEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentResultSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentRouteRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TournamentBracketProgressionService {

    private final TournamentStageRepository stageRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentMatchSlotRepository matchSlotRepository;
    private final TournamentRouteRepository routeRepository;
    private final TournamentResultSlotRepository resultSlotRepository;
    private final TournamentRepository tournamentRepository;

    public boolean isByeWinMatch(TournamentMatchEntity match, List<TournamentMatchSlotEntity> slots) {
        if (match == null || slots == null || slots.size() != 2 || !isAutoFinishable(match)) {
            return false;
        }

        long actualParticipantCount = slots.stream()
                .filter(this::isActualParticipantSlot)
                .count();
        long byeCount = slots.stream()
                .filter(this::isByeSlot)
                .count();

        return actualParticipantCount == 1 && byeCount == 1;
    }

    @Transactional
    public void applyByeWinIfNeeded(Long matchId) {
        matchRepository.findById(matchId)
                .ifPresent(match -> applyByeWinIfNeeded(match, true));
    }

    @Transactional
    public void applyByeWinsForStage(Long stageId) {
        if (!stageRepository.findById(stageId).map(this::supportsByeAutoAdvance).orElse(false)) {
            return;
        }

        List<TournamentMatchEntity> matches = matchRepository.findAllByStageIdOrderByDisplayOrderAsc(stageId);
        boolean applied;
        do {
            applied = false;
            for (TournamentMatchEntity match : matches) {
                applied = applyByeWinIfNeeded(match, false) || applied;
            }
        } while (applied);
    }

    @Transactional
    public void propagateManualResult(Long matchId, Long stageId, Long winnerParticipantId, Long loserParticipantId) {
        propagateOutcome(matchId, TournamentRouteEntity.OUTCOME_WINNER, winnerParticipantId);
        propagateOutcome(matchId, TournamentRouteEntity.OUTCOME_LOSER, loserParticipantId);
        applyByeWinsForStage(stageId);
    }

    private boolean applyByeWinIfNeeded(TournamentMatchEntity match, boolean verifyStageType) {
        if (verifyStageType && !supportsByeAutoAdvance(match.getStageId())) {
            return false;
        }
        if (!isAutoFinishable(match)) {
            return false;
        }

        List<TournamentMatchSlotEntity> slots = matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(match.getId());
        if (!isByeWinMatch(match, slots)) {
            return false;
        }

        TournamentMatchSlotEntity winnerSlot = slots.stream()
                .filter(this::isActualParticipantSlot)
                .findFirst()
                .orElseThrow();
        TournamentMatchSlotEntity byeSlot = slots.stream()
                .filter(this::isByeSlot)
                .findFirst()
                .orElseThrow();

        Long winnerParticipantId = winnerSlot.getParticipantId();
        winnerSlot.markWinner(true);
        winnerSlot.updateScore(null);
        byeSlot.markWinner(false);
        byeSlot.updateScore(null);
        match.finish(winnerParticipantId);
        propagateOutcome(match.getId(), TournamentRouteEntity.OUTCOME_WINNER, winnerParticipantId);

        return true;
    }

    private void propagateOutcome(Long fromMatchId, String outcome, Long participantId) {
        if (participantId == null) {
            return;
        }

        routeRepository.findByFromMatchIdAndOutcome(fromMatchId, outcome)
                .ifPresent(route -> applyRoute(route, participantId));
    }

    private void applyRoute(TournamentRouteEntity route, Long participantId) {
        if (route.isMatchSlotTarget()) {
            applyParticipantToMatchSlot(route, participantId);
            return;
        }
        if (route.isResultSlotTarget()) {
            applyParticipantToResultSlot(route, participantId);
        }
    }

    private void applyParticipantToMatchSlot(TournamentRouteEntity route, Long participantId) {
        if (route.getToMatchId() == null || route.getToSlotNo() == null) {
            return;
        }

        matchSlotRepository.findByMatchIdAndSlotNo(route.getToMatchId(), route.getToSlotNo())
                .ifPresent(targetSlot -> {
                    targetSlot.assignParticipant(participantId);
                    targetSlot.markBye(false);
                    targetSlot.markWinner(false);
                    targetSlot.updateScore(null);
                    markReadyIfBothParticipantsAssigned(route.getToMatchId());
                });
    }

    private void applyParticipantToResultSlot(TournamentRouteEntity route, Long participantId) {
        if (route.getToResultSlotId() == null) {
            return;
        }

        resultSlotRepository.findById(route.getToResultSlotId())
                .ifPresent(resultSlot -> {
                    resultSlot.decide(participantId, LocalDateTime.now());
                    if (isChampionResultSlot(resultSlot)) {
                        finishTournamentByStageId(resultSlot.getStageId());
                    }
                });
    }

    private boolean isChampionResultSlot(TournamentResultSlotEntity resultSlot) {
        return TournamentResultSlotEntity.TYPE_CHAMPION.equals(resultSlot.getResultType())
                || "CHAMPION".equals(resultSlot.getResultKey());
    }

    private void finishTournamentByStageId(Long stageId) {
        if (stageId == null) {
            return;
        }

        stageRepository.findById(stageId)
                .flatMap(stage -> tournamentRepository.findById(stage.getTournamentId()))
                .filter(tournament -> !TournamentEntity.STATUS_FINISHED.equals(tournament.getStatus()))
                .ifPresent(TournamentEntity::finish);
    }

    private void markReadyIfBothParticipantsAssigned(Long matchId) {
        matchRepository.findById(matchId)
                .filter(this::isAutoFinishable)
                .ifPresent(match -> {
                    List<TournamentMatchSlotEntity> slots = matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(matchId);
                    if (slots.size() == 2 && slots.stream().allMatch(this::isActualParticipantSlot)) {
                        match.markReady();
                    }
                });
    }

    private boolean supportsByeAutoAdvance(Long stageId) {
        return stageRepository.findById(stageId)
                .map(this::supportsByeAutoAdvance)
                .orElse(false);
    }

    private boolean supportsByeAutoAdvance(TournamentStageEntity stage) {
        return TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(stage.getStageType())
                || TournamentStageEntity.TYPE_DUAL_GROUP.equals(stage.getStageType());
    }

    private boolean isAutoFinishable(TournamentMatchEntity match) {
        return TournamentMatchEntity.STATUS_PENDING.equals(match.getStatus())
                || TournamentMatchEntity.STATUS_READY.equals(match.getStatus());
    }

    private boolean isActualParticipantSlot(TournamentMatchSlotEntity slot) {
        return slot.getParticipantId() != null && !isOne(slot.getIsBye());
    }

    private boolean isByeSlot(TournamentMatchSlotEntity slot) {
        return slot.getParticipantId() == null && isOne(slot.getIsBye());
    }

    private boolean isOne(Integer value) {
        return Objects.equals(value, 1);
    }
}
