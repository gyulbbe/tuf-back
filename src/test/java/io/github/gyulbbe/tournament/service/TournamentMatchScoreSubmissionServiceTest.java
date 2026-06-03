package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.map.repository.MapRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TournamentMatchScoreSubmissionServiceTest {

    private static final Long TOURNAMENT_ID = 1L;
    private static final Long STAGE_ID = 10L;
    private static final Long GROUP_ID = 20L;
    private static final Long MATCH_ID = 100L;
    private static final Long PLAYER_A_USER_ID = 101L;
    private static final Long PLAYER_B_USER_ID = 102L;
    private static final Long PLAYER_C_USER_ID = 103L;
    private static final Long PLAYER_A_PARTICIPANT_ID = 1001L;
    private static final Long PLAYER_B_PARTICIPANT_ID = 1002L;
    private static final Long PLAYER_C_PARTICIPANT_ID = 1003L;
    private static final Long MAP_ID = 700L;

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private TournamentMatchRepository matchRepository;

    @Mock
    private TournamentStageRepository stageRepository;

    @Mock
    private TournamentMatchSlotRepository matchSlotRepository;

    @Mock
    private TournamentParticipantRepository participantRepository;

    @Mock
    private TournamentMatchScoreSubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MapRepository mapRepository;

    @Mock
    private TournamentBracketProgressionService progressionService;

    @Mock
    private TournamentService tournamentService;

    @InjectMocks
    private TournamentMatchScoreSubmissionService service;

    @Test
    void submitScore_allowsPlayerAForOwnReadyMatch() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 5), internalParticipantA(), internalParticipantB());
        given(submissionRepository.save(any(TournamentMatchScoreSubmissionEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        givenSubmitterUsers(user(PLAYER_A_USER_ID, "playerA"));

        TournamentScoreSubmissionResponseDto response = service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 3), score(2, 1)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        );

        assertThat(response.getStatus()).isEqualTo(TournamentMatchScoreSubmissionEntity.STATUS_PENDING);
        assertThat(response.getSubmitterRole()).isEqualTo(TournamentMatchScoreSubmissionEntity.ROLE_PLAYER);
        assertThat(response.getSubmitterLoginId()).isEqualTo("playerA");
        assertThat(response.getSubmittedByParticipantId()).isEqualTo(PLAYER_A_PARTICIPANT_ID);
        assertThat(response.getSlot1Score()).isEqualTo(3);
        assertThat(response.getSlot2Score()).isEqualTo(1);
        assertThat(response.getWinnerSlotNo()).isEqualTo(1);
        verify(progressionService, never()).propagateManualResult(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void submitScore_allowsPlayerBAndComputesWinnerSlotNoTwo() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 5), internalParticipantA(), internalParticipantB());
        given(submissionRepository.save(any(TournamentMatchScoreSubmissionEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        givenSubmitterUsers(user(PLAYER_B_USER_ID, "playerB"));

        TournamentScoreSubmissionResponseDto response = service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 1), score(2, 3)),
                PLAYER_B_USER_ID,
                "ROLE_USER"
        );

        assertThat(response.getSubmitterRole()).isEqualTo(TournamentMatchScoreSubmissionEntity.ROLE_PLAYER);
        assertThat(response.getSubmitterLoginId()).isEqualTo("playerB");
        assertThat(response.getSubmittedByParticipantId()).isEqualTo(PLAYER_B_PARTICIPANT_ID);
        assertThat(response.getWinnerSlotNo()).isEqualTo(2);
    }

    @Test
    void submitScore_allowsAdminForAnyReadyMatch() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 3), internalParticipantA(), internalParticipantB());
        given(submissionRepository.save(any(TournamentMatchScoreSubmissionEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        givenSubmitterUsers(user(999L, "admin01"));

        TournamentScoreSubmissionResponseDto response = service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 0)),
                999L,
                "ROLE_ADMIN"
        );

        assertThat(response.getSubmitterRole()).isEqualTo(TournamentMatchScoreSubmissionEntity.ROLE_ADMIN);
        assertThat(response.getSubmitterLoginId()).isEqualTo("admin01");
        assertThat(response.getSubmittedByParticipantId()).isNull();
        assertThat(response.getWinnerSlotNo()).isEqualTo(1);
    }

    @Test
    void submitScore_requiresMapBeforeSubmitting() {
        givenReadyMatchContext(matchWithMap(TournamentMatchEntity.STATUS_READY, 3, null), internalParticipantA(), internalParticipantB());

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Map is required");
    }

    @Test
    void submitScore_requiresMapForRaceSurvivalMatch() {
        givenContext(
                tournament(),
                raceSurvivalStage(),
                matchWithMap(TournamentMatchEntity.STATUS_READY, 1, null),
                List.of(actualSlot(1, PLAYER_A_PARTICIPANT_ID), actualSlot(2, PLAYER_B_PARTICIPANT_ID)),
                List.of(internalParticipantA(), internalParticipantB())
        );

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 1), score(2, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Map is required");
    }

    @Test
    void submitScore_assignsMapFromRequestWhenMatchHasNoMap() {
        TournamentMatchEntity match = matchWithMap(TournamentMatchEntity.STATUS_READY, 3, null);
        givenReadyMatchContext(match, internalParticipantA(), internalParticipantB());
        given(submissionRepository.save(any(TournamentMatchScoreSubmissionEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        givenSubmitterUsers(user(PLAYER_A_USER_ID, "playerA"));

        TournamentScoreSubmissionResponseDto response = service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                requestWithMap(701L, score(1, 2), score(2, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        );

        assertThat(response.getMapId()).isEqualTo(701L);
        assertThat(match.getMapId()).isNull();
    }

    @Test
    void submitScore_rejectsMapChangeAfterActiveSubmissionExists() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 3), internalParticipantA(), internalParticipantB());
        given(submissionRepository.existsByTournamentIdAndMatchIdAndStatusNot(
                TOURNAMENT_ID,
                MATCH_ID,
                TournamentMatchScoreSubmissionEntity.STATUS_REJECTED
        )).willReturn(true);

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                requestWithMap(701L, score(1, 2), score(2, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("map cannot be changed");
    }

    @Test
    void submitScore_allowsRaceSurvivalTournamentParticipantOutsideCurrentMatch() {
        givenContext(
                tournament(),
                raceSurvivalStage(),
                match(TournamentMatchEntity.STATUS_READY, 3),
                List.of(actualSlot(1, PLAYER_A_PARTICIPANT_ID), actualSlot(2, PLAYER_B_PARTICIPANT_ID)),
                List.of(internalParticipantA(), internalParticipantB())
        );
        given(participantRepository.findFirstByTournamentIdAndUserIdOrderBySeedNoAscIdAsc(TOURNAMENT_ID, PLAYER_C_USER_ID))
                .willReturn(Optional.of(internalParticipantC()));
        given(submissionRepository.save(any(TournamentMatchScoreSubmissionEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        givenSubmitterUsers(user(PLAYER_C_USER_ID, "playerC"));

        TournamentScoreSubmissionResponseDto response = service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 0)),
                PLAYER_C_USER_ID,
                "ROLE_USER"
        );

        assertThat(response.getSubmitterRole()).isEqualTo(TournamentMatchScoreSubmissionEntity.ROLE_PLAYER);
        assertThat(response.getSubmitterLoginId()).isEqualTo("playerC");
        assertThat(response.getSubmittedByParticipantId()).isEqualTo(PLAYER_C_PARTICIPANT_ID);
        assertThat(response.getWinnerSlotNo()).isEqualTo(1);
    }

    @Test
    void submitScore_rejectsUnrelatedUser() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 3), internalParticipantA(), internalParticipantB());

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 0)),
                333L,
                "ROLE_USER"
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submitScore_rejectsExternalParticipantUser() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 3), externalParticipantA(), internalParticipantB());

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submitScore_rejectsInvalidBestOfScore() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 5), internalParticipantA(), internalParticipantB());

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 1)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed best-of");
    }

    @Test
    void submitScore_rejectsTieAndNegativeScores() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 3), internalParticipantA(), internalParticipantB());

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 2)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed best-of");

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, -1), score(2, 2)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero or greater");
    }

    @Test
    void submitScore_rejectsDuplicateOrMissingSlotNo() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 3), internalParticipantA(), internalParticipantB());

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(1, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(3, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("match slots");
    }

    @Test
    void submitScore_rejectsPendingFinishedAndByeMatches() {
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_PENDING, 3), internalParticipantA(), internalParticipantB());

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("READY");

        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_FINISHED, 3), internalParticipantA(), internalParticipantB());

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("READY");

        givenByeMatchContext();

        assertThatThrownBy(() -> service.submitScore(
                TOURNAMENT_ID,
                MATCH_ID,
                request(score(1, 2), score(2, 0)),
                PLAYER_A_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BYE");
    }

    @Test
    void approveSubmission_finishesMatchPropagatesRoutesAndRejectsOtherPendingSubmissions() {
        TournamentEntity tournament = tournament();
        TournamentMatchEntity match = matchWithMap(TournamentMatchEntity.STATUS_READY, 5, null);
        TournamentMatchScoreSubmissionEntity approved = submission(900L, TournamentMatchScoreSubmissionEntity.STATUS_PENDING, 3, 1, 1);
        TournamentMatchScoreSubmissionEntity other = submission(901L, TournamentMatchScoreSubmissionEntity.STATUS_PENDING, 3, 2, 1);
        TournamentMatchSlotEntity firstSlot = actualSlot(1, PLAYER_A_PARTICIPANT_ID);
        TournamentMatchSlotEntity secondSlot = actualSlot(2, PLAYER_B_PARTICIPANT_ID);

        givenContext(tournament, match, List.of(firstSlot, secondSlot), List.of(internalParticipantA(), internalParticipantB()));
        given(submissionRepository.findByIdAndTournamentIdAndMatchId(900L, TOURNAMENT_ID, MATCH_ID))
                .willReturn(Optional.of(approved));
        given(submissionRepository.findAllByTournamentIdAndMatchIdAndStatus(
                TOURNAMENT_ID,
                MATCH_ID,
                TournamentMatchScoreSubmissionEntity.STATUS_PENDING
        )).willReturn(List.of(approved, other));
        given(tournamentService.buildDetail(tournament)).willReturn(TournamentDetailResponseDto.builder().id(TOURNAMENT_ID).build());

        TournamentDetailResponseDto response = service.approveSubmission(
                TOURNAMENT_ID,
                MATCH_ID,
                900L,
                999L,
                "ROLE_ADMIN"
        );

        assertThat(response.getId()).isEqualTo(TOURNAMENT_ID);
        assertThat(match.getMapId()).isEqualTo(MAP_ID);
        assertThat(match.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_FINISHED);
        assertThat(match.getWinnerParticipantId()).isEqualTo(PLAYER_A_PARTICIPANT_ID);
        assertThat(firstSlot.getScore()).isEqualTo(3);
        assertThat(firstSlot.getIsWinner()).isEqualTo(1);
        assertThat(secondSlot.getScore()).isEqualTo(1);
        assertThat(secondSlot.getIsWinner()).isZero();
        assertThat(approved.getStatus()).isEqualTo(TournamentMatchScoreSubmissionEntity.STATUS_APPROVED);
        assertThat(other.getStatus()).isEqualTo(TournamentMatchScoreSubmissionEntity.STATUS_REJECTED);
        verify(progressionService).propagateManualResult(MATCH_ID, STAGE_ID, PLAYER_A_PARTICIPANT_ID, PLAYER_B_PARTICIPANT_ID);
    }

    @Test
    void rejectSubmission_keepsMatchReady() {
        TournamentMatchEntity match = match(TournamentMatchEntity.STATUS_READY, 3);
        TournamentMatchScoreSubmissionEntity submission = submission(900L, TournamentMatchScoreSubmissionEntity.STATUS_PENDING, 2, 0, 1);
        TournamentScoreSubmissionRejectRequestDto request = new TournamentScoreSubmissionRejectRequestDto();
        request.setAdminNote("score mismatch");

        givenReadyMatchContext(match, internalParticipantA(), internalParticipantB());
        given(submissionRepository.findByIdAndTournamentIdAndMatchId(900L, TOURNAMENT_ID, MATCH_ID))
                .willReturn(Optional.of(submission));
        givenSubmitterUsers(user(PLAYER_A_USER_ID, "playerA"));

        TournamentScoreSubmissionResponseDto response = service.rejectSubmission(
                TOURNAMENT_ID,
                MATCH_ID,
                900L,
                request,
                999L,
                "ROLE_ADMIN"
        );

        assertThat(response.getStatus()).isEqualTo(TournamentMatchScoreSubmissionEntity.STATUS_REJECTED);
        assertThat(response.getSubmitterLoginId()).isEqualTo("playerA");
        assertThat(response.getAdminNote()).isEqualTo("score mismatch");
        assertThat(match.getStatus()).isEqualTo(TournamentMatchEntity.STATUS_READY);
        verify(progressionService, never()).propagateManualResult(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void listSubmissions_allowsParticipantOrAdminButRejectsUnrelatedUser() {
        TournamentMatchScoreSubmissionEntity submission = submission(900L, TournamentMatchScoreSubmissionEntity.STATUS_PENDING, 2, 0, 1);
        givenReadyMatchContext(match(TournamentMatchEntity.STATUS_READY, 3), internalParticipantA(), internalParticipantB());
        given(submissionRepository.findAllByTournamentIdAndMatchIdAndSubmittedByUserIdOrderByRegDateDescIdDesc(
                TOURNAMENT_ID,
                MATCH_ID,
                PLAYER_A_USER_ID
        )).willReturn(List.of(submission));
        given(submissionRepository.findAllByTournamentIdAndMatchIdOrderByRegDateDescIdDesc(TOURNAMENT_ID, MATCH_ID))
                .willReturn(List.of(submission));
        givenSubmitterUsers(user(PLAYER_A_USER_ID, "playerA"));

        List<TournamentScoreSubmissionResponseDto> playerResponse = service.listSubmissions(
                TOURNAMENT_ID,
                MATCH_ID,
                PLAYER_A_USER_ID,
                "ROLE_USER"
        );

        assertThat(playerResponse).hasSize(1);
        assertThat(playerResponse.get(0).getSubmitterLoginId()).isEqualTo("playerA");

        List<TournamentScoreSubmissionResponseDto> adminResponse = service.listSubmissions(
                TOURNAMENT_ID,
                MATCH_ID,
                999L,
                "ROLE_ADMIN"
        );

        assertThat(adminResponse).hasSize(1);
        assertThat(adminResponse.get(0).getSubmitterLoginId()).isEqualTo("playerA");

        assertThatThrownBy(() -> service.listSubmissions(TOURNAMENT_ID, MATCH_ID, 333L, "ROLE_USER"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listSubmissions_allowsRaceSurvivalTournamentParticipantOutsideCurrentMatchButReturnsOwnOnly() {
        TournamentMatchScoreSubmissionEntity playerCSubmission = submission(
                902L,
                TournamentMatchScoreSubmissionEntity.STATUS_PENDING,
                2,
                0,
                1,
                PLAYER_C_USER_ID,
                PLAYER_C_PARTICIPANT_ID
        );
        givenContext(
                tournament(),
                raceSurvivalStage(),
                match(TournamentMatchEntity.STATUS_READY, 3),
                List.of(actualSlot(1, PLAYER_A_PARTICIPANT_ID), actualSlot(2, PLAYER_B_PARTICIPANT_ID)),
                List.of(internalParticipantA(), internalParticipantB())
        );
        given(participantRepository.findFirstByTournamentIdAndUserIdOrderBySeedNoAscIdAsc(TOURNAMENT_ID, PLAYER_C_USER_ID))
                .willReturn(Optional.of(internalParticipantC()));
        given(submissionRepository.findAllByTournamentIdAndMatchIdAndSubmittedByUserIdOrderByRegDateDescIdDesc(
                TOURNAMENT_ID,
                MATCH_ID,
                PLAYER_C_USER_ID
        )).willReturn(List.of(playerCSubmission));
        givenSubmitterUsers(user(PLAYER_C_USER_ID, "playerC"));

        List<TournamentScoreSubmissionResponseDto> response = service.listSubmissions(
                TOURNAMENT_ID,
                MATCH_ID,
                PLAYER_C_USER_ID,
                "ROLE_USER"
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getSubmittedByUserId()).isEqualTo(PLAYER_C_USER_ID);
        assertThat(response.get(0).getSubmitterLoginId()).isEqualTo("playerC");
        verify(submissionRepository, never())
                .findAllByTournamentIdAndMatchIdOrderByRegDateDescIdDesc(TOURNAMENT_ID, MATCH_ID);
    }

    private void givenReadyMatchContext(
            TournamentMatchEntity match,
            TournamentParticipantEntity firstParticipant,
            TournamentParticipantEntity secondParticipant
    ) {
        givenContext(
                tournament(),
                match,
                List.of(actualSlot(1, firstParticipant.getId()), actualSlot(2, secondParticipant.getId())),
                List.of(firstParticipant, secondParticipant)
        );
    }

    private void givenByeMatchContext() {
        givenContext(
                tournament(),
                match(TournamentMatchEntity.STATUS_READY, 3),
                List.of(actualSlot(1, PLAYER_A_PARTICIPANT_ID), byeSlot(2)),
                List.of(internalParticipantA())
        );
    }

    private void givenContext(
            TournamentEntity tournament,
            TournamentMatchEntity match,
            List<TournamentMatchSlotEntity> slots,
            List<TournamentParticipantEntity> participants
    ) {
        givenContext(tournament, stage(), match, slots, participants);
    }

    private void givenContext(
            TournamentEntity tournament,
            TournamentStageEntity stage,
            TournamentMatchEntity match,
            List<TournamentMatchSlotEntity> slots,
            List<TournamentParticipantEntity> participants
    ) {
        given(tournamentRepository.findById(TOURNAMENT_ID)).willReturn(Optional.of(tournament));
        given(matchRepository.findById(MATCH_ID)).willReturn(Optional.of(match));
        given(stageRepository.findById(STAGE_ID)).willReturn(Optional.of(stage));
        given(matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(MATCH_ID)).willReturn(slots);
        given(participantRepository.findAllById(any())).willReturn(participants);
        org.mockito.Mockito.lenient().when(mapRepository.existsById(anyLong())).thenReturn(true);
    }

    private TournamentEntity tournament() {
        return TournamentEntity.builder()
                .id(TOURNAMENT_ID)
                .title("Tournament")
                .status(TournamentEntity.STATUS_LIVE)
                .build();
    }

    private TournamentStageEntity stage() {
        return stage(TournamentStageEntity.TYPE_SINGLE_ELIMINATION);
    }

    private TournamentStageEntity raceSurvivalStage() {
        return stage(TournamentStageEntity.TYPE_RACE_SURVIVAL);
    }

    private TournamentStageEntity stage(String stageType) {
        return TournamentStageEntity.builder()
                .id(STAGE_ID)
                .tournamentId(TOURNAMENT_ID)
                .stageNo(1)
                .stageName("Stage")
                .stageType(stageType)
                .status(TournamentStageEntity.STATUS_READY)
                .displayOrder(1)
                .build();
    }

    private TournamentMatchEntity match(String status, Integer bestOf) {
        return matchWithMap(status, bestOf, MAP_ID);
    }

    private TournamentMatchEntity matchWithMap(String status, Integer bestOf, Long mapId) {
        return TournamentMatchEntity.builder()
                .id(MATCH_ID)
                .stageId(STAGE_ID)
                .groupId(GROUP_ID)
                .matchKey("R1M1")
                .matchRole(TournamentMatchEntity.ROLE_ROUND)
                .displayName("Round 1 Match 1")
                .bestOf(bestOf)
                .status(status)
                .mapId(mapId)
                .displayOrder(1)
                .build();
    }

    private TournamentMatchSlotEntity actualSlot(Integer slotNo, Long participantId) {
        return TournamentMatchSlotEntity.builder()
                .id(2000L + slotNo)
                .matchId(MATCH_ID)
                .slotNo(slotNo)
                .participantId(participantId)
                .isBye(0)
                .isWinner(0)
                .build();
    }

    private TournamentMatchSlotEntity byeSlot(Integer slotNo) {
        return TournamentMatchSlotEntity.builder()
                .id(2000L + slotNo)
                .matchId(MATCH_ID)
                .slotNo(slotNo)
                .placeholderLabel("BYE")
                .isBye(1)
                .isWinner(0)
                .build();
    }

    private TournamentParticipantEntity internalParticipantA() {
        return participant(PLAYER_A_PARTICIPANT_ID, PLAYER_A_USER_ID, "Player A");
    }

    private TournamentParticipantEntity internalParticipantB() {
        return participant(PLAYER_B_PARTICIPANT_ID, PLAYER_B_USER_ID, "Player B");
    }

    private TournamentParticipantEntity internalParticipantC() {
        return participant(PLAYER_C_PARTICIPANT_ID, PLAYER_C_USER_ID, "Player C");
    }

    private TournamentParticipantEntity externalParticipantA() {
        return participant(PLAYER_A_PARTICIPANT_ID, null, "External A");
    }

    private TournamentParticipantEntity participant(Long id, Long userId, String participantName) {
        return TournamentParticipantEntity.builder()
                .id(id)
                .tournamentId(TOURNAMENT_ID)
                .userId(userId)
                .participantName(participantName)
                .status(TournamentParticipantEntity.STATUS_READY)
                .build();
    }

    private void givenSubmitterUsers(UserEntity... users) {
        given(userRepository.findAllById(any())).willReturn(List.of(users));
    }

    private UserEntity user(Long id, String userId) {
        return UserEntity.builder()
                .id(id)
                .userId(userId)
                .build();
    }

    private TournamentMatchScoreSubmissionEntity submission(
            Long id,
            String status,
            Integer slot1Score,
            Integer slot2Score,
            Integer winnerSlotNo
    ) {
        return submission(id, status, slot1Score, slot2Score, winnerSlotNo, PLAYER_A_USER_ID, PLAYER_A_PARTICIPANT_ID);
    }

    private TournamentMatchScoreSubmissionEntity submission(
            Long id,
            String status,
            Integer slot1Score,
            Integer slot2Score,
            Integer winnerSlotNo,
            Long submittedByUserId,
            Long submittedByParticipantId
    ) {
        return TournamentMatchScoreSubmissionEntity.builder()
                .id(id)
                .tournamentId(TOURNAMENT_ID)
                .matchId(MATCH_ID)
                .submittedByUserId(submittedByUserId)
                .submittedByParticipantId(submittedByParticipantId)
                .submitterRole(TournamentMatchScoreSubmissionEntity.ROLE_PLAYER)
                .slot1Score(slot1Score)
                .slot2Score(slot2Score)
                .winnerSlotNo(winnerSlotNo)
                .mapId(MAP_ID)
                .status(status)
                .build();
    }

    private TournamentScoreSubmissionRequestDto request(TournamentMatchScoreRequestDto... scores) {
        TournamentScoreSubmissionRequestDto request = new TournamentScoreSubmissionRequestDto();
        request.setScores(List.of(scores));
        return request;
    }

    private TournamentScoreSubmissionRequestDto requestWithMap(Long mapId, TournamentMatchScoreRequestDto... scores) {
        TournamentScoreSubmissionRequestDto request = request(scores);
        request.setMapId(mapId);
        return request;
    }

    private TournamentMatchScoreRequestDto score(Integer slotNo, Integer score) {
        TournamentMatchScoreRequestDto request = new TournamentMatchScoreRequestDto();
        request.setSlotNo(slotNo);
        request.setScore(score);
        return request;
    }
}
