package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionMatchRequestDto;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionRequestDto;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionRejectRequestDto;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.entity.RaceSurvivalProgressSubmissionEntity;
import io.github.gyulbbe.tournament.entity.RaceSurvivalProgressSubmissionMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.RaceSurvivalProgressSubmissionMatchRepository;
import io.github.gyulbbe.tournament.repository.RaceSurvivalProgressSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupEntryRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentClanShareSendLogRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionSetRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentResultSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RaceSurvivalProgressSubmissionServiceTest {

    private static final Long TOURNAMENT_ID = 1L;
    private static final Long STAGE_ID = 10L;
    private static final Long MATCHES_GROUP_ID = 24L;
    private static final Long ACTOR_USER_ID = 101L;
    private static final Long ADMIN_USER_ID = 999L;

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private TournamentStageRepository stageRepository;

    @Mock
    private TournamentGroupRepository groupRepository;

    @Mock
    private TournamentGroupEntryRepository groupEntryRepository;

    @Mock
    private TournamentParticipantRepository participantRepository;

    @Mock
    private TournamentMatchRepository matchRepository;

    @Mock
    private TournamentMatchSlotRepository matchSlotRepository;

    @Mock
    private TournamentResultSlotRepository resultSlotRepository;

    @Mock
    private TournamentMatchScoreSubmissionRepository scoreSubmissionRepository;

    @Mock
    private TournamentMatchScoreSubmissionSetRepository scoreSubmissionSetRepository;

    @Mock
    private TournamentClanShareSendLogRepository clanShareSendLogRepository;

    @Mock
    private RaceSurvivalProgressSubmissionRepository submissionRepository;

    @Mock
    private RaceSurvivalProgressSubmissionMatchRepository submissionMatchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MapRepository mapRepository;

    @Mock
    private TournamentService tournamentService;

    @InjectMocks
    private RaceSurvivalProgressSubmissionService service;

    @Test
    void submitProgress_allowsTournamentParticipantOutsideSubmittedMatches() {
        SavedSubmissions saved = givenSubmissionSaves();
        givenRaceSurvivalContext(defaultParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1002L, ACTOR_USER_ID, "T2"));
        givenMaps();
        givenUsers();

        RaceSurvivalProgressSubmissionResponseDto response = service.submitProgress(
                TOURNAMENT_ID,
                request(
                        row(1, 1L, 1001L, 2001L, 1, 0),
                        row(2, 2L, 1001L, 2002L, 1, 0),
                        row(3, null, 1001L, 3001L, 1, 0)
                ),
                ACTOR_USER_ID,
                "ROLE_USER"
        );

        assertThat(response.getStatus()).isEqualTo(RaceSurvivalProgressSubmissionEntity.STATUS_PENDING);
        assertThat(response.getSubmitterLoginId()).isEqualTo("user101");
        assertThat(response.getMatches()).hasSize(3);
        assertThat(saved.submissions()).hasSize(1);
        assertThat(saved.matches()).extracting(RaceSurvivalProgressSubmissionMatchEntity::getMatchOrder)
                .containsExactly(1, 2, 3);
        verify(tournamentService, never()).buildDetail(any());
    }

    @Test
    void submitProgress_replacesExistingPendingSubmissionForSameUser() {
        SavedSubmissions saved = givenSubmissionSaves();
        RaceSurvivalProgressSubmissionEntity previousPending =
                submission(899L, RaceSurvivalProgressSubmissionEntity.STATUS_PENDING, ACTOR_USER_ID);
        givenRaceSurvivalContext(defaultParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1001L, ACTOR_USER_ID, "T1"));
        given(submissionRepository.findAllByTournamentIdAndSubmittedByUserIdAndStatus(
                TOURNAMENT_ID,
                ACTOR_USER_ID,
                RaceSurvivalProgressSubmissionEntity.STATUS_PENDING
        )).willReturn(List.of(previousPending));
        givenUsers();

        RaceSurvivalProgressSubmissionResponseDto response = service.submitProgress(
                TOURNAMENT_ID,
                request(
                        row(1, null, 1001L, 2001L, 1, 0),
                        row(2, null, 1001L, 2002L, 1, 0),
                        row(3, null, 1001L, 3001L, 1, 0)
                ),
                ACTOR_USER_ID,
                "ROLE_USER"
        );

        assertThat(response.getStatus()).isEqualTo(RaceSurvivalProgressSubmissionEntity.STATUS_PENDING);
        assertThat(previousPending.getStatus()).isEqualTo(RaceSurvivalProgressSubmissionEntity.STATUS_REJECTED);
        assertThat(previousPending.getReviewedByUserId()).isEqualTo(ACTOR_USER_ID);
        assertThat(saved.submissions()).hasSize(1);
        assertThat(saved.matches()).hasSize(3);
    }

    @Test
    void submitProgress_rejectsNonParticipant() {
        givenRaceSurvivalContext(defaultParticipants());
        given(participantRepository.findFirstByTournamentIdAndUserIdOrderBySeedNoAscIdAsc(TOURNAMENT_ID, ACTOR_USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitProgress(
                TOURNAMENT_ID,
                request(row(1, null, 1001L, 2001L, 1, 0)),
                ACTOR_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submitProgress_rejectsSameRaceMatch() {
        givenRaceSurvivalContext(defaultParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1001L, ACTOR_USER_ID, "T1"));

        assertThatThrownBy(() -> service.submitProgress(
                TOURNAMENT_ID,
                request(row(1, null, 1001L, 1002L, 1, 0)),
                ACTOR_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different race");
    }

    @Test
    void submitProgress_rejectsDroppedParticipantReappearing() {
        givenRaceSurvivalContext(defaultParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1001L, ACTOR_USER_ID, "T1"));
        givenMaps();

        assertThatThrownBy(() -> service.submitProgress(
                TOURNAMENT_ID,
                request(
                        row(1, 1L, 1001L, 2001L, 1, 0),
                        row(2, 2L, 2001L, 3001L, 0, 1)
                ),
                ACTOR_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dropped");
    }

    @Test
    void submitProgress_rejectsMissingPreviousWinner() {
        givenRaceSurvivalContext(defaultParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1001L, ACTOR_USER_ID, "T1"));

        assertThatThrownBy(() -> service.submitProgress(
                TOURNAMENT_ID,
                request(
                        row(1, null, 1001L, 2001L, 1, 0),
                        row(2, null, 1002L, 3001L, 1, 0)
                ),
                ACTOR_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Previous match winner");
    }

    @Test
    void submitProgress_rejectsIncompleteProgress() {
        givenRaceSurvivalContext(defaultParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1001L, ACTOR_USER_ID, "T1"));

        assertThatThrownBy(() -> service.submitProgress(
                TOURNAMENT_ID,
                request(row(1, null, 1001L, 2001L, 1, 0)),
                ACTOR_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incomplete");
    }

    @Test
    void submitProgress_rejectsDuplicateMatchOrder() {
        givenRaceSurvivalContext(defaultParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1001L, ACTOR_USER_ID, "T1"));

        assertThatThrownBy(() -> service.submitProgress(
                TOURNAMENT_ID,
                request(
                        row(1, null, 1001L, 2001L, 1, 0),
                        row(1, null, 1001L, 3001L, 1, 0)
                ),
                ACTOR_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive and unique");
    }

    @Test
    void submitProgress_rejectsInvalidScore() {
        givenRaceSurvivalContext(defaultParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1001L, ACTOR_USER_ID, "T1"));

        assertThatThrownBy(() -> service.submitProgress(
                TOURNAMENT_ID,
                request(row(1, null, 1001L, 2001L, 2, 0)),
                ACTOR_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1:0 or 0:1");
    }

    @Test
    void submitProgress_rejectsUnknownMapId() {
        givenRaceSurvivalContext(defaultParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1001L, ACTOR_USER_ID, "T1"));
        given(mapRepository.findAllById(List.of(99L))).willReturn(List.of());

        assertThatThrownBy(() -> service.submitProgress(
                TOURNAMENT_ID,
                request(
                        row(1, 99L, 1001L, 2001L, 1, 0),
                        row(2, null, 1001L, 3001L, 1, 0)
                ),
                ACTOR_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown mapId");

        verify(submissionRepository, never()).save(any(RaceSurvivalProgressSubmissionEntity.class));
    }

    @Test
    void submitProgress_acceptsUnbalancedRaceTeamSizes() {
        SavedSubmissions saved = givenSubmissionSaves();
        givenRaceSurvivalContext(unbalancedParticipants());
        givenParticipantPermission(ACTOR_USER_ID, participant(1001L, ACTOR_USER_ID, "T1"));
        givenUsers();

        RaceSurvivalProgressSubmissionResponseDto response = service.submitProgress(
                TOURNAMENT_ID,
                request(
                        row(1, null, 1001L, 2001L, 1, 0),
                        row(2, null, 1001L, 2002L, 1, 0),
                        row(3, null, 1001L, 2003L, 1, 0),
                        row(4, null, 1001L, 2004L, 1, 0),
                        row(5, null, 1001L, 3001L, 1, 0),
                        row(6, null, 1001L, 3002L, 1, 0),
                        row(7, null, 1001L, 3003L, 1, 0),
                        row(8, null, 1001L, 3004L, 1, 0),
                        row(9, null, 1001L, 3005L, 1, 0)
                ),
                ACTOR_USER_ID,
                "ROLE_USER"
        );

        assertThat(response.getMatches()).hasSize(9);
        assertThat(saved.matches()).hasSize(9);
    }

    @Test
    void approveSubmission_appliesAllOfficialMatchesAndRejectsOtherPendingSubmissions() {
        List<TournamentParticipantEntity> participants = defaultParticipants();
        TournamentEntity tournament = tournament();
        TournamentResultSlotEntity champion = resultSlot();
        RaceSurvivalProgressSubmissionEntity submission = submission(900L, RaceSurvivalProgressSubmissionEntity.STATUS_PENDING, ACTOR_USER_ID);
        RaceSurvivalProgressSubmissionEntity otherPending = submission(901L, RaceSurvivalProgressSubmissionEntity.STATUS_PENDING, 102L);
        List<TournamentMatchEntity> savedOfficialMatches = new ArrayList<>();
        List<TournamentMatchSlotEntity> savedOfficialSlots = new ArrayList<>();

        givenRaceSurvivalContext(tournament, participants);
        given(submissionRepository.findByIdAndTournamentId(900L, TOURNAMENT_ID)).willReturn(Optional.of(submission));
        given(submissionMatchRepository.findAllBySubmissionIdOrderByMatchOrderAsc(900L)).willReturn(List.of(
                storedRow(1L, 1, null, 1001L, 2001L, 1, 0),
                storedRow(2L, 2, null, 1001L, 2002L, 1, 0),
                storedRow(3L, 3, null, 1001L, 3001L, 1, 0)
        ));
        given(matchRepository.findAllByStageIdOrderByDisplayOrderAsc(STAGE_ID)).willReturn(List.of(officialPendingMatch()));
        given(matchSlotRepository.findAllByMatchIdInOrderBySlotNoAsc(List.of(500L))).willReturn(List.of(
                pendingSlot(500L, 1),
                pendingSlot(500L, 2)
        ));
        given(resultSlotRepository.findAllByStageIdOrderByRankNoAscIdAsc(STAGE_ID)).willReturn(List.of(champion));
        given(matchRepository.findAllByGroupIdOrderByDisplayOrderAsc(MATCHES_GROUP_ID)).willReturn(List.of(officialPendingMatch()));
        given(matchRepository.save(any(TournamentMatchEntity.class))).willAnswer(invocation -> {
            TournamentMatchEntity match = invocation.getArgument(0);
            assignId(match, 600L + savedOfficialMatches.size());
            savedOfficialMatches.add(match);
            return match;
        });
        given(matchSlotRepository.save(any(TournamentMatchSlotEntity.class))).willAnswer(invocation -> {
            TournamentMatchSlotEntity slot = invocation.getArgument(0);
            assignId(slot, 700L + savedOfficialSlots.size());
            savedOfficialSlots.add(slot);
            return slot;
        });
        given(resultSlotRepository.findByStageIdAndResultKey(STAGE_ID, "CHAMPION")).willReturn(Optional.of(champion));
        given(submissionRepository.findAllByTournamentIdAndStatus(
                TOURNAMENT_ID,
                RaceSurvivalProgressSubmissionEntity.STATUS_PENDING
        )).willReturn(List.of(submission, otherPending));
        given(tournamentService.buildDetail(tournament)).willReturn(TournamentDetailResponseDto.builder().id(TOURNAMENT_ID).build());

        TournamentDetailResponseDto response = service.approveSubmission(
                TOURNAMENT_ID,
                900L,
                ADMIN_USER_ID,
                "ROLE_ADMIN"
        );

        assertThat(response.getId()).isEqualTo(TOURNAMENT_ID);
        assertThat(submission.getStatus()).isEqualTo(RaceSurvivalProgressSubmissionEntity.STATUS_APPROVED);
        assertThat(otherPending.getStatus()).isEqualTo(RaceSurvivalProgressSubmissionEntity.STATUS_REJECTED);
        assertThat(tournament.getStatus()).isEqualTo(TournamentEntity.STATUS_FINISHED);
        assertThat(participantsById(participants, 2001L).getStatus()).isEqualTo(TournamentParticipantEntity.STATUS_DROPPED);
        assertThat(participantsById(participants, 2002L).getStatus()).isEqualTo(TournamentParticipantEntity.STATUS_DROPPED);
        assertThat(participantsById(participants, 3001L).getStatus()).isEqualTo(TournamentParticipantEntity.STATUS_DROPPED);
        assertThat(champion.getParticipantId()).isEqualTo(1001L);
        assertThat(savedOfficialMatches).hasSize(3);
        assertThat(savedOfficialMatches).extracting(TournamentMatchEntity::getStatus)
                .containsOnly(TournamentMatchEntity.STATUS_FINISHED);
        assertThat(savedOfficialSlots).hasSize(6);
        assertThat(savedOfficialSlots)
                .filteredOn(slot -> Integer.valueOf(1).equals(slot.getIsWinner()))
                .extracting(TournamentMatchSlotEntity::getParticipantId)
                .containsExactly(1001L, 1001L, 1001L);
    }

    @Test
    void approveSubmission_rejectsNonAdminRole() {
        assertThatThrownBy(() -> service.approveSubmission(
                TOURNAMENT_ID,
                900L,
                ACTOR_USER_ID,
                "ROLE_USER"
        )).isInstanceOf(AccessDeniedException.class);

        verify(tournamentRepository, never()).findById(anyLong());
    }

    @Test
    void approveSubmission_rejectsWhenOfficialProgressAlreadyStarted() {
        givenRaceSurvivalContext(defaultParticipants());
        given(submissionRepository.findByIdAndTournamentId(900L, TOURNAMENT_ID))
                .willReturn(Optional.of(submission(900L, RaceSurvivalProgressSubmissionEntity.STATUS_PENDING, ACTOR_USER_ID)));
        given(submissionMatchRepository.findAllBySubmissionIdOrderByMatchOrderAsc(900L)).willReturn(List.of(
                storedRow(1L, 1, null, 1001L, 2001L, 1, 0),
                storedRow(2L, 2, null, 1001L, 2002L, 1, 0),
                storedRow(3L, 3, null, 1001L, 3001L, 1, 0)
        ));
        given(matchRepository.findAllByStageIdOrderByDisplayOrderAsc(STAGE_ID)).willReturn(List.of(
                TournamentMatchEntity.builder()
                        .id(500L)
                        .stageId(STAGE_ID)
                        .groupId(MATCHES_GROUP_ID)
                        .matchKey("M1")
                        .matchRole(TournamentMatchEntity.ROLE_ROUND)
                        .displayName("Match 1")
                        .bestOf(1)
                        .status(TournamentMatchEntity.STATUS_FINISHED)
                        .winnerParticipantId(1001L)
                        .displayOrder(1)
                        .build()
        ));

        assertThatThrownBy(() -> service.approveSubmission(
                TOURNAMENT_ID,
                900L,
                ADMIN_USER_ID,
                "ROLE_ADMIN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already started");

        verify(matchRepository, never()).save(any(TournamentMatchEntity.class));
    }

    @Test
    void rejectSubmission_setsAdminNoteAndReturnsRejectedResponse() {
        RaceSurvivalProgressSubmissionEntity submission =
                submission(900L, RaceSurvivalProgressSubmissionEntity.STATUS_PENDING, ACTOR_USER_ID);
        RaceSurvivalProgressSubmissionRejectRequestDto request = new RaceSurvivalProgressSubmissionRejectRequestDto();
        request.setAdminNote("invalid progress");
        givenRaceSurvivalContext(defaultParticipants());
        given(submissionRepository.findByIdAndTournamentId(900L, TOURNAMENT_ID)).willReturn(Optional.of(submission));
        given(submissionMatchRepository.findAllBySubmissionIdOrderByMatchOrderAsc(900L)).willReturn(List.of(
                storedRow(1L, 1, null, 1001L, 2001L, 1, 0),
                storedRow(2L, 2, null, 1001L, 2002L, 1, 0),
                storedRow(3L, 3, null, 1001L, 3001L, 1, 0)
        ));
        givenUsers();

        RaceSurvivalProgressSubmissionResponseDto response = service.rejectSubmission(
                TOURNAMENT_ID,
                900L,
                request,
                ADMIN_USER_ID,
                "ROLE_ADMIN"
        );

        assertThat(response.getStatus()).isEqualTo(RaceSurvivalProgressSubmissionEntity.STATUS_REJECTED);
        assertThat(response.getAdminNote()).isEqualTo("invalid progress");
        assertThat(submission.getReviewedByUserId()).isEqualTo(ADMIN_USER_ID);
        assertThat(response.getMatches()).hasSize(3);
    }

    @Test
    void listSubmissions_returnsAllForAdminAndOwnOnlyForParticipant() {
        RaceSurvivalProgressSubmissionEntity own = submission(900L, RaceSurvivalProgressSubmissionEntity.STATUS_PENDING, ACTOR_USER_ID);
        RaceSurvivalProgressSubmissionEntity other = submission(901L, RaceSurvivalProgressSubmissionEntity.STATUS_PENDING, 102L);
        givenRaceSurvivalContext(defaultParticipants());
        given(participantRepository.findFirstByTournamentIdAndUserIdOrderBySeedNoAscIdAsc(TOURNAMENT_ID, ACTOR_USER_ID))
                .willReturn(Optional.of(participant(1001L, ACTOR_USER_ID, "T1")));
        given(submissionRepository.findAllByTournamentIdAndSubmittedByUserIdOrderByRegDateDescIdDesc(TOURNAMENT_ID, ACTOR_USER_ID))
                .willReturn(List.of(own));
        given(submissionRepository.findAllByTournamentIdOrderByRegDateDescIdDesc(TOURNAMENT_ID))
                .willReturn(List.of(own, other));
        given(submissionMatchRepository.findAllBySubmissionIdInOrderBySubmissionIdAscMatchOrderAsc(List.of(900L)))
                .willReturn(List.of(storedRow(1L, 1, null, 1001L, 2001L, 1, 0)));
        given(submissionMatchRepository.findAllBySubmissionIdInOrderBySubmissionIdAscMatchOrderAsc(List.of(900L, 901L)))
                .willReturn(List.of(
                        storedRow(1L, 1, null, 1001L, 2001L, 1, 0),
                        storedRow(901L, 2L, 1, null, 1002L, 2002L, 1, 0)
                ));
        givenUsers();

        List<RaceSurvivalProgressSubmissionResponseDto> participantResponse =
                service.listSubmissions(TOURNAMENT_ID, ACTOR_USER_ID, "ROLE_USER");
        List<RaceSurvivalProgressSubmissionResponseDto> adminResponse =
                service.listSubmissions(TOURNAMENT_ID, ADMIN_USER_ID, "ROLE_ADMIN");

        assertThat(participantResponse).hasSize(1);
        assertThat(participantResponse.get(0).getSubmittedByUserId()).isEqualTo(ACTOR_USER_ID);
        assertThat(adminResponse).hasSize(2);
    }

    private SavedSubmissions givenSubmissionSaves() {
        AtomicLong sequence = new AtomicLong(900L);
        List<RaceSurvivalProgressSubmissionEntity> submissions = new ArrayList<>();
        List<RaceSurvivalProgressSubmissionMatchEntity> matches = new ArrayList<>();
        given(submissionRepository.save(any(RaceSurvivalProgressSubmissionEntity.class))).willAnswer(invocation -> {
            RaceSurvivalProgressSubmissionEntity submission = invocation.getArgument(0);
            assignId(submission, sequence.getAndIncrement());
            submissions.add(submission);
            return submission;
        });
        given(submissionMatchRepository.save(any(RaceSurvivalProgressSubmissionMatchEntity.class))).willAnswer(invocation -> {
            RaceSurvivalProgressSubmissionMatchEntity match = invocation.getArgument(0);
            assignId(match, sequence.getAndIncrement());
            matches.add(match);
            return match;
        });
        return new SavedSubmissions(submissions, matches);
    }

    private void givenRaceSurvivalContext(List<TournamentParticipantEntity> participants) {
        givenRaceSurvivalContext(tournament(), participants);
    }

    private void givenRaceSurvivalContext(TournamentEntity tournament, List<TournamentParticipantEntity> participants) {
        given(tournamentRepository.findById(TOURNAMENT_ID)).willReturn(Optional.of(tournament));
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(TOURNAMENT_ID)).willReturn(List.of(stage()));
        given(groupRepository.findAllByStageIdOrderByDisplayOrderAsc(STAGE_ID)).willReturn(groups());
        given(groupEntryRepository.findAllByGroupIdInOrderByGroupSeedNoAsc(List.of(21L, 22L, 23L)))
                .willReturn(entries(participants));
        given(participantRepository.findAllById(any())).willReturn(participants);
    }

    private void givenParticipantPermission(Long userId, TournamentParticipantEntity participant) {
        given(participantRepository.findFirstByTournamentIdAndUserIdOrderBySeedNoAscIdAsc(TOURNAMENT_ID, userId))
                .willReturn(Optional.of(participant));
    }

    private void givenMaps() {
        given(mapRepository.findAllById(any())).willReturn(List.of(map(1L), map(2L)));
    }

    private void givenUsers() {
        given(userRepository.findAllById(any())).willReturn(List.of(
                user(101L),
                user(102L),
                user(201L),
                user(202L),
                user(301L)
        ));
    }

    private TournamentEntity tournament() {
        return TournamentEntity.builder()
                .id(TOURNAMENT_ID)
                .title("Race Survival")
                .status(TournamentEntity.STATUS_LIVE)
                .build();
    }

    private TournamentStageEntity stage() {
        return TournamentStageEntity.builder()
                .id(STAGE_ID)
                .tournamentId(TOURNAMENT_ID)
                .stageNo(1)
                .stageName("Race Survival")
                .stageType(TournamentStageEntity.TYPE_RACE_SURVIVAL)
                .status(TournamentStageEntity.STATUS_READY)
                .displayOrder(1)
                .build();
    }

    private List<TournamentGroupEntity> groups() {
        return List.of(
                group(21L, "TERRAN", 1),
                group(22L, "ZERG", 2),
                group(23L, "PROTOSS", 3),
                group(MATCHES_GROUP_ID, "MATCHES", 4)
        );
    }

    private TournamentGroupEntity group(Long id, String groupCode, Integer displayOrder) {
        return TournamentGroupEntity.builder()
                .id(id)
                .stageId(STAGE_ID)
                .groupCode(groupCode)
                .groupName(groupCode)
                .displayOrder(displayOrder)
                .build();
    }

    private List<TournamentParticipantEntity> defaultParticipants() {
        return List.of(
                participant(1001L, 101L, "T1"),
                participant(1002L, 102L, "T2"),
                participant(2001L, 201L, "Z1"),
                participant(2002L, 202L, "Z2"),
                participant(3001L, 301L, "P1")
        );
    }

    private List<TournamentParticipantEntity> unbalancedParticipants() {
        List<TournamentParticipantEntity> participants = new ArrayList<>();
        participants.add(participant(1001L, 101L, "T1"));
        participants.add(participant(1002L, 102L, "T2"));
        participants.add(participant(1003L, 103L, "T3"));
        participants.add(participant(2001L, 201L, "Z1"));
        participants.add(participant(2002L, 202L, "Z2"));
        participants.add(participant(2003L, 203L, "Z3"));
        participants.add(participant(2004L, 204L, "Z4"));
        participants.add(participant(3001L, 301L, "P1"));
        participants.add(participant(3002L, 302L, "P2"));
        participants.add(participant(3003L, 303L, "P3"));
        participants.add(participant(3004L, 304L, "P4"));
        participants.add(participant(3005L, 305L, "P5"));
        return participants;
    }

    private List<TournamentGroupEntryEntity> entries(List<TournamentParticipantEntity> participants) {
        List<TournamentGroupEntryEntity> entries = new ArrayList<>();
        int terranSeed = 1;
        int zergSeed = 1;
        int protossSeed = 1;
        for (TournamentParticipantEntity participant : participants) {
            Long participantId = participant.getId();
            if (participantId >= 1000L && participantId < 2000L) {
                entries.add(entry(21L, participantId, terranSeed++));
            } else if (participantId >= 2000L && participantId < 3000L) {
                entries.add(entry(22L, participantId, zergSeed++));
            } else {
                entries.add(entry(23L, participantId, protossSeed++));
            }
        }
        return entries;
    }

    private TournamentGroupEntryEntity entry(Long groupId, Long participantId, Integer seedNo) {
        return TournamentGroupEntryEntity.builder()
                .id(groupId * 100 + seedNo)
                .groupId(groupId)
                .participantId(participantId)
                .groupSeedNo(seedNo)
                .build();
    }

    private TournamentParticipantEntity participant(Long id, Long userId, String name) {
        return TournamentParticipantEntity.builder()
                .id(id)
                .tournamentId(TOURNAMENT_ID)
                .userId(userId)
                .participantName(name)
                .seedNo(Math.toIntExact(id % 1000))
                .status(TournamentParticipantEntity.STATUS_READY)
                .build();
    }

    private RaceSurvivalProgressSubmissionEntity submission(Long id, String status, Long submittedByUserId) {
        return RaceSurvivalProgressSubmissionEntity.builder()
                .id(id)
                .tournamentId(TOURNAMENT_ID)
                .submittedByUserId(submittedByUserId)
                .status(status)
                .build();
    }

    private TournamentMatchEntity officialPendingMatch() {
        return TournamentMatchEntity.builder()
                .id(500L)
                .stageId(STAGE_ID)
                .groupId(MATCHES_GROUP_ID)
                .matchKey("M1")
                .matchRole(TournamentMatchEntity.ROLE_ROUND)
                .displayName("Match 1")
                .bestOf(1)
                .status(TournamentMatchEntity.STATUS_PENDING)
                .displayOrder(1)
                .build();
    }

    private TournamentMatchSlotEntity pendingSlot(Long matchId, Integer slotNo) {
        return TournamentMatchSlotEntity.builder()
                .id(matchId + slotNo)
                .matchId(matchId)
                .slotNo(slotNo)
                .isWinner(0)
                .isBye(0)
                .build();
    }

    private TournamentResultSlotEntity resultSlot() {
        return TournamentResultSlotEntity.builder()
                .id(800L)
                .stageId(STAGE_ID)
                .groupId(MATCHES_GROUP_ID)
                .resultKey("CHAMPION")
                .resultType(TournamentResultSlotEntity.TYPE_CHAMPION)
                .rankNo(1)
                .label("Champion")
                .build();
    }

    private RaceSurvivalProgressSubmissionMatchEntity storedRow(
            Long id,
            Integer matchOrder,
            Long mapId,
            Long slot1ParticipantId,
            Long slot2ParticipantId,
            Integer slot1Score,
            Integer slot2Score
    ) {
        return storedRow(900L, id, matchOrder, mapId, slot1ParticipantId, slot2ParticipantId, slot1Score, slot2Score);
    }

    private RaceSurvivalProgressSubmissionMatchEntity storedRow(
            Long submissionId,
            Long id,
            Integer matchOrder,
            Long mapId,
            Long slot1ParticipantId,
            Long slot2ParticipantId,
            Integer slot1Score,
            Integer slot2Score
    ) {
        return RaceSurvivalProgressSubmissionMatchEntity.builder()
                .id(id)
                .submissionId(submissionId)
                .matchOrder(matchOrder)
                .mapId(mapId)
                .slot1ParticipantId(slot1ParticipantId)
                .slot2ParticipantId(slot2ParticipantId)
                .slot1Score(slot1Score)
                .slot2Score(slot2Score)
                .build();
    }

    private RaceSurvivalProgressSubmissionRequestDto request(RaceSurvivalProgressSubmissionMatchRequestDto... rows) {
        RaceSurvivalProgressSubmissionRequestDto request = new RaceSurvivalProgressSubmissionRequestDto();
        request.setMatches(List.of(rows));
        return request;
    }

    private RaceSurvivalProgressSubmissionMatchRequestDto row(
            Integer matchOrder,
            Long mapId,
            Long slot1ParticipantId,
            Long slot2ParticipantId,
            Integer slot1Score,
            Integer slot2Score
    ) {
        RaceSurvivalProgressSubmissionMatchRequestDto row = new RaceSurvivalProgressSubmissionMatchRequestDto();
        row.setMatchOrder(matchOrder);
        row.setMapId(mapId);
        row.setSlot1ParticipantId(slot1ParticipantId);
        row.setSlot2ParticipantId(slot2ParticipantId);
        row.setSlot1Score(slot1Score);
        row.setSlot2Score(slot2Score);
        return row;
    }

    private UserEntity user(Long id) {
        return UserEntity.builder()
                .id(id)
                .userId("user" + id)
                .build();
    }

    private MapEntity map(Long id) {
        return MapEntity.builder()
                .id(id)
                .mapName("Map " + id)
                .build();
    }

    private TournamentParticipantEntity participantsById(List<TournamentParticipantEntity> participants, Long id) {
        return participants.stream()
                .filter(participant -> participant.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private void assignId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private record SavedSubmissions(
            List<RaceSurvivalProgressSubmissionEntity> submissions,
            List<RaceSurvivalProgressSubmissionMatchEntity> matches
    ) {
    }
}
