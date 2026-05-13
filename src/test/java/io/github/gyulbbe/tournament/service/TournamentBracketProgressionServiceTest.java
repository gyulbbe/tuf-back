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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TournamentBracketProgressionServiceTest {

    private static final Long STAGE_ID = 10L;
    private static final Long GROUP_ID = 20L;

    @Mock
    private TournamentStageRepository stageRepository;

    @Mock
    private TournamentMatchRepository matchRepository;

    @Mock
    private TournamentMatchSlotRepository matchSlotRepository;

    @Mock
    private TournamentRouteRepository routeRepository;

    @Mock
    private TournamentResultSlotRepository resultSlotRepository;

    @Mock
    private TournamentRepository tournamentRepository;

    @InjectMocks
    private TournamentBracketProgressionService service;

    @Test
    void isByeWinMatch_returnsTrueForSingleActualParticipantAndByeSlot() {
        TournamentMatchEntity match = match(100L, TournamentMatchEntity.STATUS_READY, 1);
        TournamentMatchSlotEntity actualSlot = actualSlot(1000L, 100L, 1, 101L);
        TournamentMatchSlotEntity byeSlot = byeSlot(1001L, 100L, 2);

        boolean result = service.isByeWinMatch(match, List.of(actualSlot, byeSlot));

        assertThat(result).isTrue();
    }

    @Test
    void applyByeWinIfNeeded_finishesByeMatchAndPropagatesWinnerToNextReadyMatch() {
        TournamentStageEntity stage = singleEliminationStage();
        TournamentMatchEntity sourceMatch = match(100L, TournamentMatchEntity.STATUS_READY, 1);
        TournamentMatchSlotEntity actualSlot = actualSlot(1000L, 100L, 1, 101L);
        TournamentMatchSlotEntity byeSlot = byeSlot(1001L, 100L, 2);
        TournamentRouteEntity winnerRoute = matchSlotRoute(100L, 200L, 1);
        TournamentMatchEntity targetMatch = match(200L, TournamentMatchEntity.STATUS_PENDING, 2);
        TournamentMatchSlotEntity targetSlot = emptySlot(2000L, 200L, 1, "R1 winner");
        TournamentMatchSlotEntity targetOpponentSlot = actualSlot(2001L, 200L, 2, 102L);

        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage));
        given(matchRepository.findById(100L)).willReturn(Optional.of(sourceMatch));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(100L)).willReturn(List.of(actualSlot, byeSlot));
        given(routeRepository.findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_WINNER))
                .willReturn(Optional.of(winnerRoute));
        given(matchSlotRepository.findByMatchIdAndSlotNo(200L, 1)).willReturn(Optional.of(targetSlot));
        given(matchRepository.findById(200L)).willReturn(Optional.of(targetMatch));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(200L))
                .willReturn(List.of(targetSlot, targetOpponentSlot));

        service.applyByeWinIfNeeded(100L);

        assertThat(sourceMatch.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_FINISHED);
        assertThat(sourceMatch.getWinnerParticipantId()).isEqualTo(101L);
        assertThat(actualSlot.getIsWinner()).isEqualTo(1);
        assertThat(actualSlot.getScore()).isNull();
        assertThat(byeSlot.getIsWinner()).isZero();
        assertThat(byeSlot.getScore()).isNull();
        assertThat(targetSlot.getParticipantId()).isEqualTo(101L);
        assertThat(targetSlot.getIsBye()).isZero();
        assertThat(targetSlot.getIsWinner()).isZero();
        assertThat(targetSlot.getScore()).isNull();
        assertThat(targetMatch.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_READY);
        verify(routeRepository, never()).findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_LOSER);
    }

    @Test
    void applyByeWinIfNeeded_decidesResultSlotForWinnerRoute() {
        TournamentStageEntity stage = singleEliminationStage();
        TournamentEntity tournament = liveTournament();
        TournamentMatchEntity sourceMatch = match(100L, TournamentMatchEntity.STATUS_READY, 1);
        TournamentMatchSlotEntity actualSlot = actualSlot(1000L, 100L, 1, 101L);
        TournamentMatchSlotEntity byeSlot = byeSlot(1001L, 100L, 2);
        TournamentRouteEntity winnerRoute = resultSlotRoute(100L, 900L);
        TournamentResultSlotEntity resultSlot = resultSlot(900L);

        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage));
        given(matchRepository.findById(100L)).willReturn(Optional.of(sourceMatch));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(100L)).willReturn(List.of(actualSlot, byeSlot));
        given(routeRepository.findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_WINNER))
                .willReturn(Optional.of(winnerRoute));
        given(resultSlotRepository.findById(900L)).willReturn(Optional.of(resultSlot));
        given(tournamentRepository.findById(1L)).willReturn(Optional.of(tournament));

        service.applyByeWinIfNeeded(100L);

        assertThat(resultSlot.getParticipantId()).isEqualTo(101L);
        assertThat(resultSlot.getDecidedAt()).isNotNull();
        assertThat(tournament.getStatus()).isEqualTo(TournamentEntity.STATUS_FINISHED);
    }

    @Test
    void applyByeWinIfNeeded_doesNotFinishTournamentForQualifiedResultSlot() {
        TournamentStageEntity stage = singleEliminationStage();
        TournamentMatchEntity sourceMatch = match(100L, TournamentMatchEntity.STATUS_READY, 1);
        TournamentMatchSlotEntity actualSlot = actualSlot(1000L, 100L, 1, 101L);
        TournamentMatchSlotEntity byeSlot = byeSlot(1001L, 100L, 2);
        TournamentRouteEntity winnerRoute = resultSlotRoute(100L, 900L);
        TournamentResultSlotEntity resultSlot = qualifiedResultSlot(900L);

        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage));
        given(matchRepository.findById(100L)).willReturn(Optional.of(sourceMatch));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(100L)).willReturn(List.of(actualSlot, byeSlot));
        given(routeRepository.findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_WINNER))
                .willReturn(Optional.of(winnerRoute));
        given(resultSlotRepository.findById(900L)).willReturn(Optional.of(resultSlot));

        service.applyByeWinIfNeeded(100L);

        assertThat(resultSlot.getParticipantId()).isEqualTo(101L);
        assertThat(resultSlot.getDecidedAt()).isNotNull();
        verify(tournamentRepository, never()).findById(anyLong());
    }

    @Test
    void applyByeWinIfNeeded_allowsDualGroupByeMatch() {
        TournamentStageEntity stage = dualGroupStage();
        TournamentMatchEntity sourceMatch = match(100L, TournamentMatchEntity.STATUS_READY, 1);
        TournamentMatchSlotEntity actualSlot = actualSlot(1000L, 100L, 1, 101L);
        TournamentMatchSlotEntity byeSlot = byeSlot(1001L, 100L, 2);

        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage));
        given(matchRepository.findById(100L)).willReturn(Optional.of(sourceMatch));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(100L)).willReturn(List.of(actualSlot, byeSlot));
        given(routeRepository.findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_WINNER))
                .willReturn(Optional.empty());

        service.applyByeWinIfNeeded(100L);

        assertThat(sourceMatch.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_FINISHED);
        assertThat(sourceMatch.getWinnerParticipantId()).isEqualTo(101L);
        assertThat(actualSlot.getIsWinner()).isEqualTo(1);
        assertThat(byeSlot.getIsWinner()).isZero();
        verify(routeRepository, never()).findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_LOSER);
    }

    @Test
    void propagateManualResult_propagatesWinnerAndLoserRoutesAndMarksTargetsReady() {
        TournamentStageEntity stage = singleEliminationStage();
        TournamentRouteEntity winnerRoute = matchSlotRoute(100L, TournamentRouteEntity.OUTCOME_WINNER, 200L, 1);
        TournamentRouteEntity loserRoute = matchSlotRoute(100L, TournamentRouteEntity.OUTCOME_LOSER, 300L, 2);
        TournamentMatchEntity winnerTargetMatch = match(200L, TournamentMatchEntity.STATUS_PENDING, 2);
        TournamentMatchSlotEntity winnerTargetSlot = emptySlot(2000L, 200L, 1, "Winner");
        TournamentMatchSlotEntity winnerOpponentSlot = actualSlot(2001L, 200L, 2, 201L);
        TournamentMatchEntity loserTargetMatch = match(300L, TournamentMatchEntity.STATUS_PENDING, 3);
        TournamentMatchSlotEntity loserOpponentSlot = actualSlot(3000L, 300L, 1, 202L);
        TournamentMatchSlotEntity loserTargetSlot = emptySlot(3001L, 300L, 2, "Loser");

        given(routeRepository.findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_WINNER))
                .willReturn(Optional.of(winnerRoute));
        given(routeRepository.findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_LOSER))
                .willReturn(Optional.of(loserRoute));
        given(matchSlotRepository.findByMatchIdAndSlotNo(200L, 1)).willReturn(Optional.of(winnerTargetSlot));
        given(matchSlotRepository.findByMatchIdAndSlotNo(300L, 2)).willReturn(Optional.of(loserTargetSlot));
        given(matchRepository.findById(200L)).willReturn(Optional.of(winnerTargetMatch));
        given(matchRepository.findById(300L)).willReturn(Optional.of(loserTargetMatch));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(200L))
                .willReturn(List.of(winnerTargetSlot, winnerOpponentSlot));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(300L))
                .willReturn(List.of(loserOpponentSlot, loserTargetSlot));
        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage));
        given(matchRepository.findAllByStageIdOrderByDisplayOrderAsc(STAGE_ID)).willReturn(List.of());

        service.propagateManualResult(100L, STAGE_ID, 101L, 102L);

        assertThat(winnerTargetSlot.getParticipantId()).isEqualTo(101L);
        assertThat(winnerTargetSlot.getIsBye()).isZero();
        assertThat(winnerTargetSlot.getIsWinner()).isZero();
        assertThat(winnerTargetSlot.getScore()).isNull();
        assertThat(winnerTargetMatch.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_READY);
        assertThat(loserTargetSlot.getParticipantId()).isEqualTo(102L);
        assertThat(loserTargetSlot.getIsBye()).isZero();
        assertThat(loserTargetSlot.getIsWinner()).isZero();
        assertThat(loserTargetSlot.getScore()).isNull();
        assertThat(loserTargetMatch.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_READY);
    }

    @Test
    void propagateManualResult_finishesTournamentForChampionAndIgnoresEliminatedTarget() {
        TournamentStageEntity stage = singleEliminationStage();
        TournamentEntity tournament = liveTournament();
        TournamentRouteEntity winnerRoute = resultSlotRoute(100L, TournamentRouteEntity.OUTCOME_WINNER, 900L);
        TournamentRouteEntity loserRoute = eliminatedRoute(100L);
        TournamentResultSlotEntity resultSlot = resultSlot(900L);

        given(routeRepository.findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_WINNER))
                .willReturn(Optional.of(winnerRoute));
        given(routeRepository.findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_LOSER))
                .willReturn(Optional.of(loserRoute));
        given(resultSlotRepository.findById(900L)).willReturn(Optional.of(resultSlot));
        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage));
        given(tournamentRepository.findById(1L)).willReturn(Optional.of(tournament));
        given(matchRepository.findAllByStageIdOrderByDisplayOrderAsc(STAGE_ID)).willReturn(List.of());

        service.propagateManualResult(100L, STAGE_ID, 101L, 102L);

        assertThat(resultSlot.getParticipantId()).isEqualTo(101L);
        assertThat(resultSlot.getDecidedAt()).isNotNull();
        assertThat(tournament.getStatus()).isEqualTo(TournamentEntity.STATUS_FINISHED);
    }

    @Test
    void applyByeWinsForStage_cascadesByeWinnersAfterRoutePropagation() {
        TournamentStageEntity stage = singleEliminationStage();
        TournamentMatchEntity r1Match = match(100L, TournamentMatchEntity.STATUS_READY, 1);
        TournamentMatchSlotEntity r1ActualSlot = actualSlot(1000L, 100L, 1, 101L);
        TournamentMatchSlotEntity r1ByeSlot = byeSlot(1001L, 100L, 2);
        TournamentMatchEntity semifinalMatch = match(200L, TournamentMatchEntity.STATUS_PENDING, 2);
        TournamentMatchSlotEntity semifinalSourceSlot = emptySlot(2000L, 200L, 1, "R1 winner");
        TournamentMatchSlotEntity semifinalByeSlot = byeSlot(2001L, 200L, 2);
        TournamentMatchEntity finalMatch = match(300L, TournamentMatchEntity.STATUS_PENDING, 3);
        TournamentMatchSlotEntity finalSourceSlot = emptySlot(3000L, 300L, 1, "SF winner");
        TournamentMatchSlotEntity finalOpponentSlot = actualSlot(3001L, 300L, 2, 202L);

        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage));
        given(matchRepository.findAllByStageIdOrderByDisplayOrderAsc(STAGE_ID))
                .willReturn(List.of(r1Match, semifinalMatch, finalMatch));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(100L))
                .willReturn(List.of(r1ActualSlot, r1ByeSlot));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(200L))
                .willReturn(List.of(semifinalSourceSlot, semifinalByeSlot));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(300L))
                .willReturn(List.of(finalSourceSlot, finalOpponentSlot));
        given(routeRepository.findByFromMatchIdAndOutcome(100L, TournamentRouteEntity.OUTCOME_WINNER))
                .willReturn(Optional.of(matchSlotRoute(100L, 200L, 1)));
        given(routeRepository.findByFromMatchIdAndOutcome(200L, TournamentRouteEntity.OUTCOME_WINNER))
                .willReturn(Optional.of(matchSlotRoute(200L, 300L, 1)));
        given(matchSlotRepository.findByMatchIdAndSlotNo(200L, 1)).willReturn(Optional.of(semifinalSourceSlot));
        given(matchSlotRepository.findByMatchIdAndSlotNo(300L, 1)).willReturn(Optional.of(finalSourceSlot));
        given(matchRepository.findById(200L)).willReturn(Optional.of(semifinalMatch));
        given(matchRepository.findById(300L)).willReturn(Optional.of(finalMatch));

        service.applyByeWinsForStage(STAGE_ID);

        assertThat(r1Match.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_FINISHED);
        assertThat(semifinalMatch.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_FINISHED);
        assertThat(semifinalMatch.getWinnerParticipantId()).isEqualTo(101L);
        assertThat(finalSourceSlot.getParticipantId()).isEqualTo(101L);
        assertThat(finalMatch.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_READY);
    }

    @Test
    void isByeWinMatch_returnsFalseWhenBothSlotsHaveParticipants() {
        TournamentMatchEntity match = match(100L, TournamentMatchEntity.STATUS_READY, 1);

        boolean result = service.isByeWinMatch(
                match,
                List.of(actualSlot(1000L, 100L, 1, 101L), actualSlot(1001L, 100L, 2, 102L))
        );

        assertThat(result).isFalse();
    }

    @Test
    void applyByeWinIfNeeded_doesNothingWhenNoActualParticipantExists() {
        TournamentStageEntity stage = singleEliminationStage();
        TournamentMatchEntity match = match(100L, TournamentMatchEntity.STATUS_READY, 1);
        TournamentMatchSlotEntity firstByeSlot = byeSlot(1000L, 100L, 1);
        TournamentMatchSlotEntity secondByeSlot = byeSlot(1001L, 100L, 2);

        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage));
        given(matchRepository.findById(100L)).willReturn(Optional.of(match));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(100L))
                .willReturn(List.of(firstByeSlot, secondByeSlot));

        service.applyByeWinIfNeeded(100L);

        assertThat(match.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_READY);
        assertThat(match.getWinnerParticipantId()).isNull();
        assertThat(firstByeSlot.getIsWinner()).isZero();
        assertThat(secondByeSlot.getIsWinner()).isZero();
        verify(routeRepository, never()).findByFromMatchIdAndOutcome(anyLong(), anyString());
    }

    private TournamentStageEntity singleEliminationStage() {
        return TournamentStageEntity.builder()
                .id(STAGE_ID)
                .tournamentId(1L)
                .stageNo(1)
                .stageName("Single Elimination")
                .stageType(TournamentStageEntity.TYPE_SINGLE_ELIMINATION)
                .status(TournamentStageEntity.STATUS_READY)
                .displayOrder(1)
                .build();
    }

    private TournamentEntity liveTournament() {
        return TournamentEntity.builder()
                .id(1L)
                .title("Tournament")
                .status(TournamentEntity.STATUS_LIVE)
                .build();
    }

    private TournamentStageEntity dualGroupStage() {
        return TournamentStageEntity.builder()
                .id(STAGE_ID)
                .tournamentId(1L)
                .stageNo(1)
                .stageName("Dual Group")
                .stageType(TournamentStageEntity.TYPE_DUAL_GROUP)
                .status(TournamentStageEntity.STATUS_READY)
                .displayOrder(1)
                .build();
    }

    private TournamentMatchEntity match(Long id, String status, Integer displayOrder) {
        return TournamentMatchEntity.builder()
                .id(id)
                .stageId(STAGE_ID)
                .groupId(GROUP_ID)
                .matchKey("M" + id)
                .matchRole(TournamentMatchEntity.ROLE_ROUND)
                .displayName("Match " + id)
                .bestOf(3)
                .status(status)
                .displayOrder(displayOrder)
                .build();
    }

    private TournamentMatchSlotEntity actualSlot(Long id, Long matchId, Integer slotNo, Long participantId) {
        return TournamentMatchSlotEntity.builder()
                .id(id)
                .matchId(matchId)
                .slotNo(slotNo)
                .participantId(participantId)
                .isWinner(0)
                .isBye(0)
                .build();
    }

    private TournamentMatchSlotEntity byeSlot(Long id, Long matchId, Integer slotNo) {
        return TournamentMatchSlotEntity.builder()
                .id(id)
                .matchId(matchId)
                .slotNo(slotNo)
                .placeholderLabel("BYE")
                .isWinner(0)
                .isBye(1)
                .build();
    }

    private TournamentMatchSlotEntity emptySlot(Long id, Long matchId, Integer slotNo, String placeholderLabel) {
        return TournamentMatchSlotEntity.builder()
                .id(id)
                .matchId(matchId)
                .slotNo(slotNo)
                .placeholderLabel(placeholderLabel)
                .isWinner(0)
                .isBye(0)
                .build();
    }

    private TournamentRouteEntity matchSlotRoute(Long fromMatchId, Long toMatchId, Integer toSlotNo) {
        return matchSlotRoute(fromMatchId, TournamentRouteEntity.OUTCOME_WINNER, toMatchId, toSlotNo);
    }

    private TournamentRouteEntity matchSlotRoute(Long fromMatchId, String outcome, Long toMatchId, Integer toSlotNo) {
        return TournamentRouteEntity.builder()
                .fromMatchId(fromMatchId)
                .outcome(outcome)
                .targetType(TournamentRouteEntity.TARGET_MATCH_SLOT)
                .toMatchId(toMatchId)
                .toSlotNo(toSlotNo)
                .build();
    }

    private TournamentRouteEntity resultSlotRoute(Long fromMatchId, Long toResultSlotId) {
        return resultSlotRoute(fromMatchId, TournamentRouteEntity.OUTCOME_WINNER, toResultSlotId);
    }

    private TournamentRouteEntity resultSlotRoute(Long fromMatchId, String outcome, Long toResultSlotId) {
        return TournamentRouteEntity.builder()
                .fromMatchId(fromMatchId)
                .outcome(outcome)
                .targetType(TournamentRouteEntity.TARGET_RESULT_SLOT)
                .toResultSlotId(toResultSlotId)
                .build();
    }

    private TournamentRouteEntity eliminatedRoute(Long fromMatchId) {
        return TournamentRouteEntity.builder()
                .fromMatchId(fromMatchId)
                .outcome(TournamentRouteEntity.OUTCOME_LOSER)
                .targetType(TournamentRouteEntity.TARGET_ELIMINATED)
                .build();
    }

    private TournamentResultSlotEntity resultSlot(Long id) {
        return TournamentResultSlotEntity.builder()
                .id(id)
                .stageId(STAGE_ID)
                .groupId(GROUP_ID)
                .resultKey("CHAMPION")
                .resultType(TournamentResultSlotEntity.TYPE_CHAMPION)
                .rankNo(1)
                .label("Champion")
                .build();
    }

    private TournamentResultSlotEntity qualifiedResultSlot(Long id) {
        return TournamentResultSlotEntity.builder()
                .id(id)
                .stageId(STAGE_ID)
                .groupId(GROUP_ID)
                .resultKey("A_1ST")
                .resultType(TournamentResultSlotEntity.TYPE_QUALIFIED)
                .rankNo(1)
                .label("A 1st")
                .build();
    }
}
