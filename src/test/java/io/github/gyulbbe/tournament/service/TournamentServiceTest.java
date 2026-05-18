package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.dto.TournamentSummaryResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentGroupEntryRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentResultSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private TournamentParticipantRepository participantRepository;

    @Mock
    private TournamentStageRepository stageRepository;

    @Mock
    private TournamentGroupRepository groupRepository;

    @Mock
    private TournamentGroupEntryRepository groupEntryRepository;

    @Mock
    private TournamentMatchRepository matchRepository;

    @Mock
    private TournamentMatchSlotRepository matchSlotRepository;

    @Mock
    private TournamentResultSlotRepository resultSlotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MapRepository mapRepository;

    @InjectMocks
    private TournamentService tournamentService;

    @Test
    void getPublicTournament_succeedsWithExternalParticipantsOnly() {
        TournamentParticipantEntity externalParticipant = externalParticipant(10L, "External Invite", 1);

        givenPublicTournament(1L);
        given(participantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(1L))
                .willReturn(List.of(externalParticipant));
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(1L)).willReturn(List.of());

        var response = tournamentService.getPublicTournament(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getParticipants()).hasSize(1);
        assertThat(response.getData().getParticipants().get(0).getUserId()).isNull();
        assertThat(response.getData().getParticipants().get(0).getParticipantName()).isEqualTo("External Invite");
        assertThat(response.getData().getParticipants().get(0).getDisplayName()).isEqualTo("External Invite");
        assertThat(response.getData().getParticipants().get(0).getUserLoginId()).isNull();
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void getPublicTournament_usesUserLoginIdForInternalParticipant() {
        TournamentParticipantEntity internalParticipant = internalParticipant(10L, 100L, 1);
        UserEntity user = user(100L, "member01", "Member Name");

        givenPublicTournament(1L);
        given(participantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(1L))
                .willReturn(List.of(internalParticipant));
        given(userRepository.findAllById(any())).willReturn(List.of(user));
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(1L)).willReturn(List.of());

        var response = tournamentService.getPublicTournament(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getParticipants()).hasSize(1);
        assertThat(response.getData().getParticipants().get(0).getDisplayName()).isEqualTo("member01");
        assertThat(response.getData().getParticipants().get(0).getDisplayName()).isNotEqualTo("Member Name");
        assertThat(response.getData().getParticipants().get(0).getUserLoginId()).isEqualTo("member01");
        assertThat(response.getData().getParticipants().get(0).getParticipantName()).isNull();
    }

    @Test
    void getPublicTournament_keepsStoredParticipantNameForInternalParticipant() {
        TournamentParticipantEntity internalParticipant = internalParticipant(10L, 100L, "Stored Alias", 1);
        UserEntity user = user(100L, "member01", "Member Name");

        givenPublicTournament(1L);
        given(participantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(1L))
                .willReturn(List.of(internalParticipant));
        given(userRepository.findAllById(any())).willReturn(List.of(user));
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(1L)).willReturn(List.of());

        var response = tournamentService.getPublicTournament(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getParticipants()).hasSize(1);
        assertThat(response.getData().getParticipants().get(0).getDisplayName()).isEqualTo("member01");
        assertThat(response.getData().getParticipants().get(0).getParticipantName()).isEqualTo("Stored Alias");
    }

    @Test
    void getPublicTournament_succeedsWithMixedInternalAndExternalParticipants() {
        TournamentParticipantEntity internalParticipant = internalParticipant(10L, 100L, 1);
        TournamentParticipantEntity externalParticipant = externalParticipant(11L, "External Player", 2);
        UserEntity user = user(100L, "member01", "Member Name");

        givenPublicTournament(1L);
        given(participantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(1L))
                .willReturn(List.of(internalParticipant, externalParticipant));
        given(userRepository.findAllById(any())).willReturn(List.of(user));
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(1L)).willReturn(List.of());

        var response = tournamentService.getPublicTournament(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getParticipants()).hasSize(2);
        assertThat(response.getData().getParticipants().get(0).getDisplayName()).isEqualTo("member01");
        assertThat(response.getData().getParticipants().get(0).getDisplayName()).isNotEqualTo("Member Name");
        assertThat(response.getData().getParticipants().get(0).getParticipantName()).isNull();
        assertThat(response.getData().getParticipants().get(1).getDisplayName()).isEqualTo("External Player");
        assertThat(response.getData().getParticipants().get(1).getParticipantName()).isEqualTo("External Player");
    }

    @Test
    void listPublicTournaments_usesLiveAndFinishedStatusesOnly() {
        TournamentEntity liveTournament = tournament(1L, TournamentEntity.STATUS_LIVE);
        TournamentEntity finishedTournament = tournament(2L, TournamentEntity.STATUS_FINISHED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> statusesCaptor = ArgumentCaptor.forClass(List.class);

        given(tournamentRepository.findAllByStatusInOrderByUpdateDateDescRegDateDesc(anyList()))
                .willReturn(List.of(liveTournament, finishedTournament));
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(1L)).willReturn(List.of());
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(2L)).willReturn(List.of());
        given(participantRepository.countByTournamentId(1L)).willReturn(0L);
        given(participantRepository.countByTournamentId(2L)).willReturn(0L);

        var response = tournamentService.listPublicTournaments();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).hasSize(2);
        verify(tournamentRepository).findAllByStatusInOrderByUpdateDateDescRegDateDesc(statusesCaptor.capture());
        assertThat(statusesCaptor.getValue())
                .containsExactly(TournamentEntity.STATUS_LIVE, TournamentEntity.STATUS_FINISHED);
    }

    @Test
    void listPublicTournaments_includesBracketTypeFromFirstStage() {
        TournamentEntity singleTournament = tournament(1L, TournamentEntity.STATUS_LIVE);
        TournamentEntity dualTournament = tournament(2L, TournamentEntity.STATUS_LIVE);
        TournamentStageEntity singleStage = stage(101L, TournamentStageEntity.TYPE_SINGLE_ELIMINATION);
        TournamentStageEntity dualStage = stage(201L, TournamentStageEntity.TYPE_DUAL_GROUP);

        given(tournamentRepository.findAllByStatusInOrderByUpdateDateDescRegDateDesc(anyList()))
                .willReturn(List.of(singleTournament, dualTournament));
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(1L)).willReturn(List.of(singleStage));
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(2L)).willReturn(List.of(dualStage));
        given(groupRepository.countByStageIdIn(List.of(singleStage.getId()))).willReturn(1L);
        given(groupRepository.countByStageIdIn(List.of(dualStage.getId()))).willReturn(2L);
        given(participantRepository.countByTournamentId(1L)).willReturn(0L);
        given(participantRepository.countByTournamentId(2L)).willReturn(0L);

        var response = tournamentService.listPublicTournaments();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).extracting(TournamentSummaryResponseDto::getBracketType)
                .containsExactly(
                        TournamentStageEntity.TYPE_SINGLE_ELIMINATION,
                        TournamentStageEntity.TYPE_DUAL_GROUP
                );
    }

    @Test
    void getPublicTournament_succeedsWithFinishedTournament() {
        givenPublicTournament(1L, TournamentEntity.STATUS_FINISHED);
        given(participantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(1L)).willReturn(List.of());
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(1L)).willReturn(List.of());

        var response = tournamentService.getPublicTournament(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getStatus()).isEqualTo(TournamentEntity.STATUS_FINISHED);
    }

    @Test
    void getPublicTournament_appliesLoginIdDisplayNameToGroupMatchSlotAndResultSlotParticipants() {
        TournamentParticipantEntity internalParticipant = internalParticipant(10L, 100L, "Stored Alias", 1);
        TournamentParticipantEntity externalParticipant = externalParticipant(11L, "External Player", 2);
        UserEntity user = user(100L, "member01", "Member Name");
        TournamentStageEntity stage = stage(100L);
        TournamentGroupEntity group = group(200L, stage.getId());
        TournamentMatchEntity match = match(300L, stage.getId(), group.getId());

        givenPublicTournament(1L);
        given(participantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(1L))
                .willReturn(List.of(internalParticipant, externalParticipant));
        given(userRepository.findAllById(any())).willReturn(List.of(user));
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(1L)).willReturn(List.of(stage));
        given(groupRepository.findAllByStageIdInOrderByDisplayOrderAsc(List.of(stage.getId()))).willReturn(List.of(group));
        given(groupEntryRepository.findAllByGroupIdInOrderByGroupSeedNoAsc(List.of(group.getId())))
                .willReturn(List.of(groupEntry(400L, group.getId(), internalParticipant.getId(), 1),
                        groupEntry(401L, group.getId(), externalParticipant.getId(), 2)));
        given(matchRepository.findAllByGroupIdInOrderByDisplayOrderAsc(List.of(group.getId()))).willReturn(List.of(match));
        given(matchSlotRepository.findAllByMatchIdInOrderBySlotNoAsc(List.of(match.getId())))
                .willReturn(List.of(matchSlot(500L, match.getId(), 1, internalParticipant.getId()),
                        matchSlot(501L, match.getId(), 2, externalParticipant.getId())));
        given(resultSlotRepository.findAllByGroupIdInOrderByRankNoAscIdAsc(List.of(group.getId())))
                .willReturn(List.of(resultSlot(600L, stage.getId(), group.getId(), internalParticipant.getId())));

        var response = tournamentService.getPublicTournament(1L);

        assertThat(response.getStatus()).isEqualTo(200);

        var groupParticipant = response.getData().getGroups().get(0).getParticipants().get(0);
        assertThat(groupParticipant.getDisplayName()).isEqualTo("member01");
        assertThat(groupParticipant.getDisplayName()).isNotEqualTo("Member Name");
        assertThat(groupParticipant.getParticipantName()).isEqualTo("Stored Alias");

        var matchSlotParticipant = response.getData().getGroups().get(0)
                .getMatches().get(0)
                .getSlots().get(0)
                .getParticipant();
        assertThat(matchSlotParticipant.getDisplayName()).isEqualTo("member01");
        assertThat(matchSlotParticipant.getParticipantName()).isEqualTo("Stored Alias");

        var resultSlotParticipant = response.getData().getGroups().get(0)
                .getResultSlots().get(0)
                .getParticipant();
        assertThat(resultSlotParticipant.getDisplayName()).isEqualTo("member01");
        assertThat(resultSlotParticipant.getParticipantName()).isEqualTo("Stored Alias");
    }

    @Test
    void getPublicTournament_includesMatchMapName() {
        TournamentStageEntity stage = stage(100L);
        TournamentGroupEntity group = group(200L, stage.getId());
        TournamentMatchEntity match = TournamentMatchEntity.builder()
                .id(300L)
                .stageId(stage.getId())
                .groupId(group.getId())
                .matchKey("M1")
                .matchRole(TournamentMatchEntity.ROLE_ROUND)
                .displayName("Match 1")
                .bestOf(1)
                .status(TournamentMatchEntity.STATUS_READY)
                .mapId(700L)
                .displayOrder(1)
                .build();
        MapEntity map = MapEntity.builder()
                .id(700L)
                .mapName("Polypoid")
                .build();

        givenPublicTournament(1L);
        given(participantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(1L)).willReturn(List.of());
        given(stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(1L)).willReturn(List.of(stage));
        given(groupRepository.findAllByStageIdInOrderByDisplayOrderAsc(List.of(stage.getId()))).willReturn(List.of(group));
        given(groupEntryRepository.findAllByGroupIdInOrderByGroupSeedNoAsc(List.of(group.getId()))).willReturn(List.of());
        given(matchRepository.findAllByGroupIdInOrderByDisplayOrderAsc(List.of(group.getId()))).willReturn(List.of(match));
        given(matchSlotRepository.findAllByMatchIdInOrderBySlotNoAsc(List.of(match.getId()))).willReturn(List.of());
        given(resultSlotRepository.findAllByGroupIdInOrderByRankNoAscIdAsc(List.of(group.getId()))).willReturn(List.of());
        given(mapRepository.findAllById(List.of(map.getId()))).willReturn(List.of(map));

        var response = tournamentService.getPublicTournament(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        var responseMatch = response.getData().getGroups().get(0).getMatches().get(0);
        assertThat(responseMatch.getMapId()).isEqualTo(700L);
        assertThat(responseMatch.getMapName()).isEqualTo("Polypoid");
    }

    private void givenPublicTournament(Long tournamentId) {
        givenPublicTournament(tournamentId, TournamentEntity.STATUS_LIVE);
    }

    private void givenPublicTournament(Long tournamentId, String status) {
        given(tournamentRepository.findByIdAndStatusIn(eq(tournamentId), anyList()))
                .willReturn(Optional.of(tournament(tournamentId, status)));
    }

    private TournamentEntity tournament(Long tournamentId, String status) {
        return TournamentEntity.builder()
                .id(tournamentId)
                .title("Public Tournament")
                .status(status)
                .build();
    }

    private TournamentParticipantEntity externalParticipant(Long id, String participantName, Integer seedNo) {
        return TournamentParticipantEntity.builder()
                .id(id)
                .tournamentId(1L)
                .participantName(participantName)
                .seedNo(seedNo)
                .status(TournamentParticipantEntity.STATUS_READY)
                .build();
    }

    private TournamentParticipantEntity internalParticipant(Long id, Long userId, Integer seedNo) {
        return internalParticipant(id, userId, null, seedNo);
    }

    private TournamentParticipantEntity internalParticipant(Long id, Long userId, String participantName, Integer seedNo) {
        return TournamentParticipantEntity.builder()
                .id(id)
                .tournamentId(1L)
                .userId(userId)
                .participantName(participantName)
                .seedNo(seedNo)
                .status(TournamentParticipantEntity.STATUS_READY)
                .build();
    }

    private TournamentStageEntity stage(Long id) {
        return stage(id, TournamentStageEntity.TYPE_SINGLE_ELIMINATION);
    }

    private TournamentStageEntity stage(Long id, String stageType) {
        return TournamentStageEntity.builder()
                .id(id)
                .tournamentId(1L)
                .stageNo(1)
                .stageName("Main")
                .stageType(stageType)
                .status(TournamentStageEntity.STATUS_READY)
                .displayOrder(1)
                .build();
    }

    private TournamentGroupEntity group(Long id, Long stageId) {
        return TournamentGroupEntity.builder()
                .id(id)
                .stageId(stageId)
                .groupCode("MAIN")
                .groupName("Main")
                .displayOrder(1)
                .build();
    }

    private TournamentGroupEntryEntity groupEntry(Long id, Long groupId, Long participantId, Integer groupSeedNo) {
        return TournamentGroupEntryEntity.builder()
                .id(id)
                .groupId(groupId)
                .participantId(participantId)
                .groupSeedNo(groupSeedNo)
                .build();
    }

    private TournamentMatchEntity match(Long id, Long stageId, Long groupId) {
        return TournamentMatchEntity.builder()
                .id(id)
                .stageId(stageId)
                .groupId(groupId)
                .matchKey("R1M1")
                .matchRole(TournamentMatchEntity.ROLE_ROUND)
                .roundNo(1)
                .matchNo(1)
                .displayName("Round 1 Match 1")
                .bestOf(3)
                .status(TournamentMatchEntity.STATUS_READY)
                .displayOrder(1)
                .build();
    }

    private TournamentMatchSlotEntity matchSlot(Long id, Long matchId, Integer slotNo, Long participantId) {
        return TournamentMatchSlotEntity.builder()
                .id(id)
                .matchId(matchId)
                .slotNo(slotNo)
                .participantId(participantId)
                .isWinner(0)
                .isBye(0)
                .build();
    }

    private TournamentResultSlotEntity resultSlot(Long id, Long stageId, Long groupId, Long participantId) {
        return TournamentResultSlotEntity.builder()
                .id(id)
                .stageId(stageId)
                .groupId(groupId)
                .resultKey("CHAMPION")
                .resultType(TournamentResultSlotEntity.TYPE_CHAMPION)
                .rankNo(1)
                .label("Champion")
                .participantId(participantId)
                .build();
    }

    private UserEntity user(Long id, String userId, String name) {
        return UserEntity.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .userType("ROLE_USER")
                .status("ACTIVE")
                .build();
    }
}
