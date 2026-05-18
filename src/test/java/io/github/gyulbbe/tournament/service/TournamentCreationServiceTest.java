package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.tournament.dto.TournamentCreateGroupRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateSlotRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentRouteEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentGroupEntryRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionRepository;
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
    private TournamentMatchSlotRepository matchSlotRepository;

    @Mock
    private TournamentRouteRepository routeRepository;

    @Mock
    private TournamentResultSlotRepository resultSlotRepository;

    @Mock
    private UserRepository userRepository;

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
        List<TournamentMatchSlotEntity> matchSlots = new ArrayList<>();
        List<TournamentRouteEntity> routes = new ArrayList<>();
        List<TournamentResultSlotEntity> resultSlots = new ArrayList<>();

        given(tournamentRepository.save(any(TournamentEntity.class))).willAnswer(saveAnswer(sequence, tournaments));
        given(participantRepository.save(any(TournamentParticipantEntity.class))).willAnswer(saveAnswer(sequence, participants));
        given(stageRepository.save(any(TournamentStageEntity.class))).willAnswer(saveAnswer(sequence, stages));
        given(groupRepository.save(any(TournamentGroupEntity.class))).willAnswer(saveAnswer(sequence, groups));
        given(groupEntryRepository.save(any(TournamentGroupEntryEntity.class))).willAnswer(saveAnswer(sequence, entries));
        given(matchRepository.save(any(TournamentMatchEntity.class))).willAnswer(saveAnswer(sequence, matches));
        given(matchSlotRepository.save(any(TournamentMatchSlotEntity.class))).willAnswer(saveAnswer(sequence, matchSlots));
        given(routeRepository.save(any(TournamentRouteEntity.class))).willAnswer(saveAnswer(sequence, routes));
        given(resultSlotRepository.save(any(TournamentResultSlotEntity.class))).willAnswer(saveAnswer(sequence, resultSlots));

        return new SavedEntities(tournaments, participants, stages, groups, entries, matches, matchSlots, routes, resultSlots);
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

    private record SavedEntities(
            List<TournamentEntity> tournaments,
            List<TournamentParticipantEntity> participants,
            List<TournamentStageEntity> stages,
            List<TournamentGroupEntity> groups,
            List<TournamentGroupEntryEntity> entries,
            List<TournamentMatchEntity> matches,
            List<TournamentMatchSlotEntity> matchSlots,
            List<TournamentRouteEntity> routes,
            List<TournamentResultSlotEntity> resultSlots
    ) {
    }
}
