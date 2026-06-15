package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.dto.TournamentCreateGroupRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateMapDefaultRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateMatchDefaultRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateSlotRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSetEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentRouteEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentGroupEntryRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentClanShareSendLogRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionSetRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSetRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentResultSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentRouteRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TournamentCreationServiceTest {

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
    private TournamentMatchScoreSubmissionRepository scoreSubmissionRepository;

    @Mock
    private TournamentMatchScoreSubmissionSetRepository scoreSubmissionSetRepository;

    @Mock
    private TournamentClanShareSendLogRepository clanShareSendLogRepository;

    @Mock
    private TournamentMatchSetRepository matchSetRepository;

    @Mock
    private TournamentMatchSlotRepository matchSlotRepository;

    @Mock
    private TournamentRouteRepository routeRepository;

    @Mock
    private TournamentResultSlotRepository resultSlotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MapRepository mapRepository;

    @Mock
    private TournamentBracketProgressionService bracketProgressionService;

    @Mock
    private TournamentService tournamentService;

    @InjectMocks
    private TournamentCreationService service;

    @Test
    void createTournament_createsSingleEliminationByesWithoutByeParticipant() {
        SavedEntities saved = stubGeneratedIds();
        TournamentCreateRequestDto request = request(
                "Single",
                TournamentStageEntity.TYPE_SINGLE_ELIMINATION,
                group(null, null,
                        externalSlot(1, "P1"),
                        externalSlot(2, "P2"),
                        externalSlot(3, "P3"),
                        externalSlot(4, "P4"),
                        externalSlot(5, "P5"))
        );
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        service.createTournament(request, 99L);

        assertThat(saved.tournaments()).hasSize(1);
        assertThat(saved.tournaments().get(0).getStatus()).isEqualTo(TournamentEntity.STATUS_LIVE);
        assertThat(saved.participants()).hasSize(5);
        assertThat(saved.participants()).noneMatch(participant -> "BYE".equalsIgnoreCase(participant.getParticipantName()));
        assertThat(saved.matchSlots())
                .filteredOn(slot -> Integer.valueOf(1).equals(slot.getIsBye()))
                .hasSize(3);
        assertThat(saved.resultSlots())
                .extracting(TournamentResultSlotEntity::getResultKey)
                .contains("CHAMPION", "RUNNER_UP");
        verify(bracketProgressionService).applyByeWinsForStage(anyLong());
    }

    @Test
    void createTournament_ignoresPublishNowTrueAndCreatesLiveTournament() {
        SavedEntities saved = stubGeneratedIds();
        TournamentCreateRequestDto request = request(
                "Single",
                TournamentStageEntity.TYPE_SINGLE_ELIMINATION,
                group(null, null,
                        externalSlot(1, "P1"),
                        externalSlot(2, "P2"))
        );
        request.setPublishNow(true);
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        service.createTournament(request, 99L);

        assertThat(saved.tournaments()).hasSize(1);
        assertThat(saved.tournaments().get(0).getStatus()).isEqualTo(TournamentEntity.STATUS_LIVE);
    }

    @Test
    void createTournament_ignoresMissingPublishNowAndCreatesLiveTournament() {
        SavedEntities saved = stubGeneratedIds();
        TournamentCreateRequestDto request = request(
                "Single",
                TournamentStageEntity.TYPE_SINGLE_ELIMINATION,
                group(null, null,
                        externalSlot(1, "P1"),
                        externalSlot(2, "P2"))
        );
        request.setPublishNow(null);
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        service.createTournament(request, 99L);

        assertThat(saved.tournaments()).hasSize(1);
        assertThat(saved.tournaments().get(0).getStatus()).isEqualTo(TournamentEntity.STATUS_LIVE);
    }

    @Test
    void createTournament_createsDualGroupByeAndOmitsByeLoserRoute() {
        SavedEntities saved = stubGeneratedIds();
        TournamentCreateRequestDto request = request(
                "Dual",
                TournamentStageEntity.TYPE_DUAL_GROUP,
                group("A", "A Group",
                        externalSlot(1, "A1"),
                        externalSlot(2, "A2"),
                        externalSlot(3, "A3"),
                        externalSlot(4, "A4")),
                group("B", "B Group",
                        externalSlot(1, "B1"),
                        externalSlot(2, "B2"),
                        externalSlot(3, "B3"))
        );
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        service.createTournament(request, 99L);

        TournamentMatchEntity b2 = saved.matches().stream()
                .filter(match -> "B2".equals(match.getMatchKey()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.participants()).hasSize(7);
        assertThat(saved.matchSlots())
                .filteredOn(slot -> Integer.valueOf(1).equals(slot.getIsBye()))
                .isNotEmpty();
        assertThat(saved.routes()).noneMatch(route ->
                Objects.equals(route.getFromMatchId(), b2.getId())
                        && TournamentRouteEntity.OUTCOME_LOSER.equals(route.getOutcome()));
        assertThat(saved.routes()).anyMatch(route ->
                Objects.equals(route.getFromMatchId(), b2.getId())
                        && TournamentRouteEntity.OUTCOME_WINNER.equals(route.getOutcome()));
        verify(bracketProgressionService).applyByeWinsForStage(anyLong());
    }

    @Test
    void createTournament_createsTwoParticipantDualGroupWithoutLosersMatch() {
        SavedEntities saved = stubGeneratedIds();
        TournamentCreateRequestDto request = request(
                "Dual",
                TournamentStageEntity.TYPE_DUAL_GROUP,
                group("A", "A Group",
                        externalSlot(1, "A1"),
                        externalSlot(3, "A2"))
        );
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        service.createTournament(request, 99L);

        TournamentMatchEntity winners = saved.matches().stream()
                .filter(match -> "AW".equals(match.getMatchKey()))
                .findFirst()
                .orElseThrow();
        TournamentMatchEntity decider = saved.matches().stream()
                .filter(match -> "AF".equals(match.getMatchKey()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.matches())
                .extracting(TournamentMatchEntity::getMatchKey)
                .containsExactly("A1", "A2", "AW", "AF");
        assertThat(saved.matches())
                .noneMatch(match -> TournamentMatchEntity.ROLE_LOSERS.equals(match.getMatchRole()));
        assertThat(saved.matchSlots())
                .anyMatch(slot ->
                        Objects.equals(slot.getMatchId(), decider.getId())
                                && Integer.valueOf(2).equals(slot.getSlotNo())
                                && Integer.valueOf(1).equals(slot.getIsBye()));
        assertThat(saved.routes()).anyMatch(route ->
                Objects.equals(route.getFromMatchId(), winners.getId())
                        && TournamentRouteEntity.OUTCOME_LOSER.equals(route.getOutcome())
                        && Objects.equals(route.getToMatchId(), decider.getId())
                        && Integer.valueOf(1).equals(route.getToSlotNo()));
        verify(bracketProgressionService).applyByeWinsForStage(anyLong());
    }

    @Test
    void createTournament_rejectsDualGroupWithOneParticipant() {
        TournamentCreateRequestDto request = request(
                "Dual",
                TournamentStageEntity.TYPE_DUAL_GROUP,
                group("A", "A Group",
                        externalSlot(1, "A1"))
        );

        assertThatThrownBy(() -> service.createTournament(request, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two participants");
    }

    @Test
    void createTournament_rejectsTwoParticipantDualGroupInSameOpeningMatch() {
        TournamentCreateRequestDto request = request(
                "Dual",
                TournamentStageEntity.TYPE_DUAL_GROUP,
                group("A", "A Group",
                        externalSlot(1, "A1"),
                        externalSlot(2, "A2"))
        );

        assertThatThrownBy(() -> service.createTournament(request, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one participant in each opening match");
    }

    @Test
    void createTournament_appliesSingleEliminationRoundMapDefaults() {
        SavedEntities saved = stubGeneratedIds();
        TournamentCreateRequestDto request = request(
                "Single",
                TournamentStageEntity.TYPE_SINGLE_ELIMINATION,
                group(null, null,
                        externalSlot(1, "P1"),
                        externalSlot(2, "P2"),
                        externalSlot(3, "P3"),
                        externalSlot(4, "P4"),
                        externalSlot(5, "P5"),
                        externalSlot(6, "P6"),
                        externalSlot(7, "P7"),
                        externalSlot(8, "P8"))
        );
        request.setMapDefaults(List.of(
                roundMapDefault(1, 700L),
                roundMapDefault(2, 701L),
                roundMapDefault(3, 702L)
        ));
        given(mapRepository.findAllById(any())).willReturn(List.of(map(700L), map(701L), map(702L)));
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        service.createTournament(request, 99L);

        assertThat(saved.matches())
                .filteredOn(match -> Integer.valueOf(1).equals(match.getRoundNo()))
                .extracting(TournamentMatchEntity::getMapId)
                .containsOnly(700L);
        assertThat(saved.matches())
                .filteredOn(match -> Integer.valueOf(2).equals(match.getRoundNo()))
                .extracting(TournamentMatchEntity::getMapId)
                .containsOnly(701L);
        assertThat(saved.matches())
                .filteredOn(match -> Integer.valueOf(3).equals(match.getRoundNo()))
                .extracting(TournamentMatchEntity::getMapId)
                .containsOnly(702L);
    }

    @Test
    void createTournament_appliesDualGroupRoleMapDefaults() {
        SavedEntities saved = stubGeneratedIds();
        TournamentCreateRequestDto request = request(
                "Dual",
                TournamentStageEntity.TYPE_DUAL_GROUP,
                group("A", "A Group",
                        externalSlot(1, "A1"),
                        externalSlot(2, "A2"),
                        externalSlot(3, "A3"),
                        externalSlot(4, "A4"))
        );
        request.setMapDefaults(List.of(
                roleMapDefault(TournamentMatchEntity.ROLE_OPENING, 700L),
                roleMapDefault(TournamentMatchEntity.ROLE_WINNERS, 701L),
                roleMapDefault(TournamentMatchEntity.ROLE_LOSERS, 702L),
                roleMapDefault(TournamentMatchEntity.ROLE_DECIDER, 703L)
        ));
        given(mapRepository.findAllById(any())).willReturn(List.of(map(700L), map(701L), map(702L), map(703L)));
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        service.createTournament(request, 99L);

        assertThat(saved.matches())
                .filteredOn(match -> TournamentMatchEntity.ROLE_OPENING.equals(match.getMatchRole()))
                .extracting(TournamentMatchEntity::getMapId)
                .containsOnly(700L);
        assertThat(saved.matches())
                .filteredOn(match -> TournamentMatchEntity.ROLE_WINNERS.equals(match.getMatchRole()))
                .extracting(TournamentMatchEntity::getMapId)
                .containsOnly(701L);
        assertThat(saved.matches())
                .filteredOn(match -> TournamentMatchEntity.ROLE_LOSERS.equals(match.getMatchRole()))
                .extracting(TournamentMatchEntity::getMapId)
                .containsOnly(702L);
        assertThat(saved.matches())
                .filteredOn(match -> TournamentMatchEntity.ROLE_DECIDER.equals(match.getMatchRole()))
                .extracting(TournamentMatchEntity::getMapId)
                .containsOnly(703L);
    }

    @Test
    void createTournament_appliesUltimateBattleMapDefault() {
        SavedEntities saved = stubGeneratedIds();
        TournamentCreateRequestDto request = request(
                "Ultimate",
                TournamentStageEntity.TYPE_ULTIMATE_BATTLE,
                group(null, null,
                        externalSlot(1, "P1"),
                        externalSlot(2, "P2"))
        );
        request.setMapDefaults(List.of(roleMapDefault(TournamentMatchEntity.ROLE_FINAL, 700L)));
        given(mapRepository.findAllById(any())).willReturn(List.of(map(700L)));
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        service.createTournament(request, 99L);

        assertThat(saved.matches()).extracting(TournamentMatchEntity::getMapId).containsOnly(700L);
    }

    @Test
    void createTournament_appliesUltimateBattleMatchDefaults() {
        SavedEntities saved = stubGeneratedIds();
        TournamentCreateRequestDto request = request(
                "Ultimate",
                TournamentStageEntity.TYPE_ULTIMATE_BATTLE,
                group(null, null,
                        externalSlot(1, "P1"),
                        externalSlot(2, "P2"))
        );
        request.setMatchDefaults(List.of(
                roleMatchDefault(
                        TournamentMatchEntity.ROLE_FINAL,
                        5,
                        java.util.Arrays.asList(700L, 701L, null, 703L, 704L)
                )
        ));
        given(mapRepository.findAllById(any())).willReturn(List.of(
                map(700L),
                map(701L),
                map(703L),
                map(704L)
        ));
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        service.createTournament(request, 99L);

        TournamentMatchEntity finalMatch = saved.matches().get(0);
        assertThat(finalMatch.getBestOf()).isEqualTo(5);
        assertThat(finalMatch.getMapId()).isEqualTo(700L);
        assertThat(saved.matchSets())
                .extracting(TournamentMatchSetEntity::getSetNo)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(saved.matchSets())
                .extracting(TournamentMatchSetEntity::getMapId)
                .containsExactly(700L, 701L, null, 703L, 704L);
    }

    @Test
    void createTournament_rejectsUltimateBattleNonFinalMatchDefault() {
        TournamentCreateRequestDto request = request(
                "Ultimate",
                TournamentStageEntity.TYPE_ULTIMATE_BATTLE,
                group(null, null,
                        externalSlot(1, "P1"),
                        externalSlot(2, "P2"))
        );
        request.setMatchDefaults(List.of(
                roleMatchDefault(TournamentMatchEntity.ROLE_WINNERS, 5, List.of(700L))
        ));

        assertThatThrownBy(() -> service.createTournament(request, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ULTIMATE_BATTLE matchDefaults require FINAL");
    }

    @Test
    void createTournament_rejectsInvalidMapDefaults() {
        TournamentCreateRequestDto unknownMapRequest = request(
                "Single",
                TournamentStageEntity.TYPE_SINGLE_ELIMINATION,
                group(null, null, externalSlot(1, "P1"), externalSlot(2, "P2"))
        );
        unknownMapRequest.setMapDefaults(List.of(roundMapDefault(1, 700L)));
        given(mapRepository.findAllById(any())).willReturn(List.of());

        assertThatThrownBy(() -> service.createTournament(unknownMapRequest, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown mapId");

        TournamentCreateRequestDto duplicateRequest = request(
                "Single",
                TournamentStageEntity.TYPE_SINGLE_ELIMINATION,
                group(null, null, externalSlot(1, "P1"), externalSlot(2, "P2"))
        );
        duplicateRequest.setMapDefaults(List.of(
                roundMapDefault(1, 700L),
                roundMapDefault(1, 701L)
        ));

        assertThatThrownBy(() -> service.createTournament(duplicateRequest, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate ROUND mapDefault");

        TournamentCreateRequestDto raceRequest = request(
                "Race",
                TournamentStageEntity.TYPE_RACE_SURVIVAL,
                group("TERRAN", "TERRAN", externalSlot(1, "T1")),
                group("ZERG", "ZERG", externalSlot(1, "Z1")),
                group("PROTOSS", "PROTOSS", externalSlot(1, "P1"))
        );
        raceRequest.setMapDefaults(List.of(roleMapDefault(TournamentMatchEntity.ROLE_FINAL, 700L)));

        assertThatThrownBy(() -> service.createTournament(raceRequest, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RACE_SURVIVAL does not accept mapDefaults");
    }

    @Test
    void createTournament_rejectsSingleEliminationWithOneParticipant() {
        TournamentCreateRequestDto request = request(
                "Single",
                TournamentStageEntity.TYPE_SINGLE_ELIMINATION,
                group(null, null, externalSlot(1, "P1"))
        );

        assertThatThrownBy(() -> service.createTournament(request, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least two participants");
    }

    @Test
    void createTournament_rejectsDuplicateUserId() {
        TournamentCreateRequestDto request = request(
                "Dual",
                TournamentStageEntity.TYPE_DUAL_GROUP,
                group("A", "A Group",
                        userSlot(1, 10L),
                        userSlot(2, 10L))
        );

        assertThatThrownBy(() -> service.createTournament(request, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate userId");
    }

    private SavedEntities stubGeneratedIds() {
        AtomicLong sequence = new AtomicLong(1L);
        List<TournamentEntity> tournaments = new ArrayList<>();
        List<TournamentParticipantEntity> participants = new ArrayList<>();
        List<TournamentStageEntity> stages = new ArrayList<>();
        List<TournamentGroupEntity> groups = new ArrayList<>();
        List<TournamentGroupEntryEntity> entries = new ArrayList<>();
        List<TournamentMatchEntity> matches = new ArrayList<>();
        List<TournamentMatchSetEntity> matchSets = new ArrayList<>();
        List<TournamentMatchSlotEntity> matchSlots = new ArrayList<>();
        List<TournamentRouteEntity> routes = new ArrayList<>();
        List<TournamentResultSlotEntity> resultSlots = new ArrayList<>();

        given(tournamentRepository.save(any(TournamentEntity.class))).willAnswer(saveAnswer(sequence, tournaments));
        given(participantRepository.save(any(TournamentParticipantEntity.class))).willAnswer(saveAnswer(sequence, participants));
        given(stageRepository.save(any(TournamentStageEntity.class))).willAnswer(saveAnswer(sequence, stages));
        given(groupRepository.save(any(TournamentGroupEntity.class))).willAnswer(saveAnswer(sequence, groups));
        given(groupEntryRepository.save(any(TournamentGroupEntryEntity.class))).willAnswer(saveAnswer(sequence, entries));
        given(matchRepository.save(any(TournamentMatchEntity.class))).willAnswer(saveAnswer(sequence, matches));
        org.mockito.Mockito.lenient().when(matchSetRepository.save(any(TournamentMatchSetEntity.class)))
                .thenAnswer(saveAnswer(sequence, matchSets));
        given(matchSlotRepository.save(any(TournamentMatchSlotEntity.class))).willAnswer(saveAnswer(sequence, matchSlots));
        given(routeRepository.save(any(TournamentRouteEntity.class))).willAnswer(saveAnswer(sequence, routes));
        given(resultSlotRepository.save(any(TournamentResultSlotEntity.class))).willAnswer(saveAnswer(sequence, resultSlots));

        return new SavedEntities(tournaments, participants, stages, groups, entries, matches, matchSets, matchSlots, routes, resultSlots);
    }

    private <T> Answer<T> saveAnswer(AtomicLong sequence, List<T> savedEntities) {
        return invocation -> {
            T entity = invocation.getArgument(0);
            assignId(entity, sequence.getAndIncrement());
            savedEntities.add(entity);
            return entity;
        };
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

    private TournamentCreateRequestDto request(
            String title,
            String bracketType,
            TournamentCreateGroupRequestDto... groups
    ) {
        TournamentCreateRequestDto request = new TournamentCreateRequestDto();
        request.setTitle(title);
        request.setBracketType(bracketType);
        request.setBestOf(3);
        request.setPublishNow(false);
        request.setGroups(List.of(groups));
        return request;
    }

    private TournamentCreateGroupRequestDto group(
            String groupCode,
            String groupName,
            TournamentCreateSlotRequestDto... slots
    ) {
        TournamentCreateGroupRequestDto group = new TournamentCreateGroupRequestDto();
        group.setGroupCode(groupCode);
        group.setGroupName(groupName);
        group.setSlots(List.of(slots));
        return group;
    }

    private TournamentCreateSlotRequestDto externalSlot(Integer slotNo, String participantName) {
        TournamentCreateSlotRequestDto slot = new TournamentCreateSlotRequestDto();
        slot.setSlotNo(slotNo);
        slot.setParticipantName(participantName);
        return slot;
    }

    private TournamentCreateSlotRequestDto userSlot(Integer slotNo, Long userId) {
        TournamentCreateSlotRequestDto slot = new TournamentCreateSlotRequestDto();
        slot.setSlotNo(slotNo);
        slot.setUserId(userId);
        return slot;
    }

    private TournamentCreateMapDefaultRequestDto roundMapDefault(Integer roundNo, Long mapId) {
        TournamentCreateMapDefaultRequestDto mapDefault = new TournamentCreateMapDefaultRequestDto();
        mapDefault.setTarget("ROUND");
        mapDefault.setRoundNo(roundNo);
        mapDefault.setMapId(mapId);
        return mapDefault;
    }

    private TournamentCreateMapDefaultRequestDto roleMapDefault(String matchRole, Long mapId) {
        TournamentCreateMapDefaultRequestDto mapDefault = new TournamentCreateMapDefaultRequestDto();
        mapDefault.setTarget("MATCH_ROLE");
        mapDefault.setMatchRole(matchRole);
        mapDefault.setMapId(mapId);
        return mapDefault;
    }

    private TournamentCreateMatchDefaultRequestDto roleMatchDefault(String matchRole, Integer bestOf, List<Long> mapIds) {
        TournamentCreateMatchDefaultRequestDto matchDefault = new TournamentCreateMatchDefaultRequestDto();
        matchDefault.setTarget("MATCH_ROLE");
        matchDefault.setMatchRole(matchRole);
        matchDefault.setBestOf(bestOf);
        matchDefault.setMapIds(mapIds);
        return matchDefault;
    }

    private MapEntity map(Long id) {
        return MapEntity.builder()
                .id(id)
                .mapName("Map " + id)
                .build();
    }

    private record SavedEntities(
            List<TournamentEntity> tournaments,
            List<TournamentParticipantEntity> participants,
            List<TournamentStageEntity> stages,
            List<TournamentGroupEntity> groups,
            List<TournamentGroupEntryEntity> entries,
            List<TournamentMatchEntity> matches,
            List<TournamentMatchSetEntity> matchSets,
            List<TournamentMatchSlotEntity> matchSlots,
            List<TournamentRouteEntity> routes,
            List<TournamentResultSlotEntity> resultSlots
    ) {
    }
}
