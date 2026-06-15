package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogSummaryResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentClanShareSendLogEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentClanShareSendLogRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TournamentClanShareSendLogServiceTest {

    private static final Long TOURNAMENT_ID = 1L;
    private static final Long STAGE_ID = 10L;
    private static final Long GROUP_ID = 20L;
    private static final Long MATCH_ID = 100L;
    private static final Long ADMIN_USER_ID = 999L;

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private TournamentStageRepository stageRepository;

    @Mock
    private TournamentMatchRepository matchRepository;

    @Mock
    private TournamentClanShareSendLogRepository logRepository;

    @InjectMocks
    private TournamentClanShareSendLogService service;

    @Test
    void getSummary_returnsNoHistoryWhenLogCountIsZero() {
        given(tournamentRepository.existsById(TOURNAMENT_ID)).willReturn(true);
        given(logRepository.countByTournamentId(TOURNAMENT_ID)).willReturn(0L);
        given(logRepository.findFirstByTournamentIdOrderByRegDateDescIdDesc(TOURNAMENT_ID))
                .willReturn(Optional.empty());

        TournamentClanShareSendLogSummaryResponseDto response = service.getSummary(TOURNAMENT_ID);

        assertThat(response.isHasHistory()).isFalse();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getLatestSentAt()).isNull();
    }

    @Test
    void getSummary_returnsHistoryWhenAnyLogExists() {
        LocalDateTime sentAt = LocalDateTime.of(2026, 5, 31, 13, 0);
        given(tournamentRepository.existsById(TOURNAMENT_ID)).willReturn(true);
        given(logRepository.countByTournamentId(TOURNAMENT_ID)).willReturn(3L);
        given(logRepository.findFirstByTournamentIdOrderByRegDateDescIdDesc(TOURNAMENT_ID))
                .willReturn(Optional.of(TournamentClanShareSendLogEntity.builder()
                        .tournamentId(TOURNAMENT_ID)
                        .matchId(MATCH_ID)
                        .sendGroupId("group-1")
                        .player1("A")
                        .player2("B")
                        .winner("A")
                        .loser("B")
                        .mapName("투혼")
                        .matchType("개인리그")
                        .matchName("테스트")
                        .playedDate("2026-05-31")
                        .eloStatus(TournamentClanShareSendLogEntity.STATUS_SUCCESS)
                        .sheetStatus(TournamentClanShareSendLogEntity.STATUS_SUCCESS)
                        .requestedByUserId(ADMIN_USER_ID)
                        .regDate(sentAt)
                        .build()));

        TournamentClanShareSendLogSummaryResponseDto response = service.getSummary(TOURNAMENT_ID);

        assertThat(response.isHasHistory()).isTrue();
        assertThat(response.getTotalCount()).isEqualTo(3L);
        assertThat(response.getLatestSentAt()).isEqualTo(sentAt);
    }

    @Test
    void createLog_savesSuccessAndFailureStatuses() {
        givenMatchInTournament();
        given(logRepository.save(any(TournamentClanShareSendLogEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        TournamentClanShareSendLogRequestDto request = request();
        request.setEloStatus(TournamentClanShareSendLogEntity.STATUS_FAILED);
        request.setEloMessage("ELO user not found");
        request.setSheetStatus(TournamentClanShareSendLogEntity.STATUS_SUCCESS);
        request.setSheetMessage("SUCCESS");

        TournamentClanShareSendLogResponseDto response = service.createLog(request, ADMIN_USER_ID);

        assertThat(response.getEloStatus()).isEqualTo(TournamentClanShareSendLogEntity.STATUS_FAILED);
        assertThat(response.getEloMessage()).isEqualTo("ELO user not found");
        assertThat(response.getSheetStatus()).isEqualTo(TournamentClanShareSendLogEntity.STATUS_SUCCESS);
        ArgumentCaptor<TournamentClanShareSendLogEntity> captor = ArgumentCaptor.forClass(
                TournamentClanShareSendLogEntity.class
        );
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestedByUserId()).isEqualTo(ADMIN_USER_ID);
    }

    @Test
    void createLog_rejectsUnknownStatus() {
        TournamentClanShareSendLogRequestDto request = request();
        request.setEloStatus("PENDING");

        assertThatThrownBy(() -> service.createLog(request, ADMIN_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eloStatus");
        verify(logRepository, never()).save(any());
    }

    @Test
    void createLog_rejectsMatchOutsideTournament() {
        given(tournamentRepository.existsById(TOURNAMENT_ID)).willReturn(true);
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match()));
        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage(999L)));

        assertThatThrownBy(() -> service.createLog(request(), ADMIN_USER_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Match not found in tournament");
        verify(logRepository, never()).save(any());
    }

    private void givenMatchInTournament() {
        given(tournamentRepository.existsById(TOURNAMENT_ID)).willReturn(true);
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match()));
        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage(TOURNAMENT_ID)));
    }

    private TournamentMatchEntity match() {
        return TournamentMatchEntity.builder()
                .id(MATCH_ID)
                .stageId(STAGE_ID)
                .groupId(GROUP_ID)
                .matchKey("R1M1")
                .matchRole(TournamentMatchEntity.ROLE_ROUND)
                .displayName("Round 1 Match 1")
                .bestOf(3)
                .status(TournamentMatchEntity.STATUS_FINISHED)
                .displayOrder(1)
                .build();
    }

    private TournamentStageEntity stage(Long tournamentId) {
        return TournamentStageEntity.builder()
                .id(STAGE_ID)
                .tournamentId(tournamentId)
                .stageNo(1)
                .stageName("Stage")
                .stageType(TournamentStageEntity.TYPE_SINGLE_ELIMINATION)
                .status(TournamentStageEntity.STATUS_READY)
                .displayOrder(1)
                .build();
    }

    private TournamentClanShareSendLogRequestDto request() {
        TournamentClanShareSendLogRequestDto request = new TournamentClanShareSendLogRequestDto();
        request.setTournamentId(TOURNAMENT_ID);
        request.setMatchId(MATCH_ID);
        request.setSendGroupId("00000000-0000-0000-0000-000000000000");
        request.setPlayer1("A");
        request.setPlayer2("B");
        request.setWinner("A");
        request.setLoser("B");
        request.setMapName("투혼");
        request.setMatchType("개인리그");
        request.setMatchName("테스트 대회");
        request.setPlayedDate("2026-05-31");
        request.setEloStatus(TournamentClanShareSendLogEntity.STATUS_SUCCESS);
        request.setEloMessage("SUCCESS");
        request.setSheetStatus(TournamentClanShareSendLogEntity.STATUS_SUCCESS);
        request.setSheetMessage("SUCCESS");
        return request;
    }
}
