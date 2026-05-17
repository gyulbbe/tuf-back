package io.github.gyulbbe.home.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.home.dto.AdminHomeScheduleCreateRequest;
import io.github.gyulbbe.home.dto.AdminHomeScheduleDeleteRequest;
import io.github.gyulbbe.home.dto.AdminHomeScheduleDeleteResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleMapSearchResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleMatchPlayerRequest;
import io.github.gyulbbe.home.dto.AdminHomeScheduleMatchRequest;
import io.github.gyulbbe.home.dto.AdminHomeScheduleProleagueTeamSearchResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleUpdateRequest;
import io.github.gyulbbe.home.dto.HomeScheduleResponse;
import io.github.gyulbbe.home.entity.HomeScheduleEntity;
import io.github.gyulbbe.home.entity.HomeScheduleMatchEntity;
import io.github.gyulbbe.home.entity.HomeScheduleMatchPlayerEntity;
import io.github.gyulbbe.home.repository.HomeScheduleMatchPlayerRepository;
import io.github.gyulbbe.home.repository.HomeScheduleMatchRepository;
import io.github.gyulbbe.home.repository.HomeScheduleProleagueTeamQueryRepository;
import io.github.gyulbbe.home.repository.HomeScheduleRepository;
import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeScheduleServiceTest {

    @Mock
    private HomeScheduleRepository homeScheduleRepository;

    @Mock
    private HomeScheduleMatchRepository homeScheduleMatchRepository;

    @Mock
    private HomeScheduleMatchPlayerRepository homeScheduleMatchPlayerRepository;

    @Mock
    private HomeScheduleProleagueTeamQueryRepository proleagueTeamQueryRepository;

    @Mock
    private MapRepository mapRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HomeScheduleService homeScheduleService;

    @Test
    void listPublicSchedules_returnsMatchesPlayersAndLoginIdDisplayName() {
        HomeScheduleEntity schedule = schedule(1L, "PROLEAGUE", "Proleague", HomeScheduleEntity.LINK_TYPE_DIRECT);
        HomeScheduleMatchEntity oneVsOne = match(10L, 1L, 1, "SET 1", "1V1", 100L);
        HomeScheduleMatchEntity teamPlay = match(11L, 1L, 2, "SET 2", "2V2", 101L);
        HomeScheduleMatchPlayerEntity internalPlayer = player(1000L, 10L, "A", 1, 7L, "Stored Name", null, null);
        HomeScheduleMatchPlayerEntity externalPlayer = player(1001L, 10L, "B", 1, null, "external_b", "A", "TERRAN");
        HomeScheduleMatchPlayerEntity teamA1 = player(1100L, 11L, "A", 1, null, "alpha_1", "S", "PROTOSS");
        HomeScheduleMatchPlayerEntity teamA2 = player(1101L, 11L, "A", 2, null, "alpha_2", "A", "ZERG");
        HomeScheduleMatchPlayerEntity teamB1 = player(1102L, 11L, "B", 1, null, "bravo_1", "A", "TERRAN");
        HomeScheduleMatchPlayerEntity teamB2 = player(1103L, 11L, "B", 2, null, "bravo_2", "A", "RANDOM");

        when(homeScheduleRepository.findPublicRepresentativeSchedules(any(LocalDateTime.class))).thenReturn(List.of(schedule));
        when(homeScheduleMatchRepository.findByScheduleIdInOrderByScheduleIdAscDisplayOrderAscIdAsc(List.of(1L)))
                .thenReturn(List.of(oneVsOne, teamPlay));
        when(homeScheduleMatchPlayerRepository.findByMatchIdInOrderByMatchIdAscSideAscSlotOrderAscIdAsc(List.of(10L, 11L)))
                .thenReturn(List.of(internalPlayer, externalPlayer, teamA1, teamA2, teamB1, teamB2));
        when(mapRepository.findAllById(List.of(100L, 101L))).thenReturn(List.of(map(100L, "Neo Dark Origin"), map(101L, "Huntress")));
        when(userRepository.findAllById(List.of(7L))).thenReturn(List.of(user(7L, "login_alpha", "Real Name", "S", "PROTOSS")));

        ResponseDto<List<HomeScheduleResponse>> response = homeScheduleService.listPublicSchedules(20);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getMatches()).hasSize(2);
        assertThat(response.getData().get(0).getMatches().get(0).getMapName()).isEqualTo("Neo Dark Origin");
        assertThat(response.getData().get(0).getMatches().get(0).getSideAPlayers().get(0).getPlayerName()).isEqualTo("login_alpha");
        assertThat(response.getData().get(0).getMatches().get(0).getSideAPlayers().get(0).getPlayerRank()).isEqualTo("S");
        assertThat(response.getData().get(0).getMatches().get(0).getSideBPlayers().get(0).getPlayerName()).isEqualTo("external_b");
        assertThat(response.getData().get(0).getMatches().get(1).getSideAPlayers())
                .extracting("slotOrder")
                .containsExactly(1, 2);
    }

    @Test
    void createSchedule_savesMatchesAndPlayersTogether() {
        AdminHomeScheduleCreateRequest request = createRequest();
        when(mapRepository.findAllById(any())).thenReturn(List.of(map(100L, "Neo Dark Origin")));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(7L, "login_alpha", "Real Name", "S", "PROTOSS")));
        when(homeScheduleRepository.save(any(HomeScheduleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<HomeScheduleEntity> scheduleCaptor = ArgumentCaptor.forClass(HomeScheduleEntity.class);

        ResponseDto<AdminHomeScheduleResponse> response = homeScheduleService.createSchedule(request);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(homeScheduleRepository).save(scheduleCaptor.capture());
        HomeScheduleEntity saved = scheduleCaptor.getValue();
        assertThat(saved.getMatches()).hasSize(1);
        assertThat(saved.getMatches().get(0).getPlayers()).hasSize(2);
        assertThat(response.getData().getMatches()).hasSize(1);
        assertThat(response.getData().getMatches().get(0).getSideAPlayers().get(0).getPlayerName()).isEqualTo("login_alpha");
    }

    @Test
    void createSchedule_handlesNullShadowForeignKeysInSavedRelations() {
        AdminHomeScheduleCreateRequest request = createRequest();
        HomeScheduleEntity[] savedHolder = new HomeScheduleEntity[1];
        when(mapRepository.findAllById(any())).thenReturn(List.of(map(100L, "Neo Dark Origin")));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(7L, "login_alpha", "Real Name", "S", "PROTOSS")));
        when(homeScheduleRepository.save(any(HomeScheduleEntity.class))).thenAnswer(invocation -> {
            HomeScheduleEntity incoming = invocation.getArgument(0);
            HomeScheduleMatchEntity incomingMatch = incoming.getMatches().get(0);
            HomeScheduleMatchEntity savedMatch = match(
                    10L,
                    null,
                    incomingMatch.getDisplayOrder(),
                    incomingMatch.getSetLabel(),
                    incomingMatch.getMatchFormat(),
                    incomingMatch.getMapId()
            );
            savedMatch.replacePlayers(incomingMatch.getPlayers().stream()
                    .map(sourcePlayer -> player(
                            sourcePlayer.getId(),
                            null,
                            sourcePlayer.getSide(),
                            sourcePlayer.getSlotOrder(),
                            sourcePlayer.getUserId(),
                            sourcePlayer.getPlayerName(),
                            sourcePlayer.getPlayerRank(),
                            sourcePlayer.getPlayerRace()
                    ))
                    .toList());
            HomeScheduleEntity saved = schedule(1L, incoming.getScheduleGroup(), incoming.getTitle(), incoming.getLinkType());
            saved.replaceMatches(List.of(savedMatch));
            savedHolder[0] = saved;
            return saved;
        });
        when(homeScheduleMatchRepository.findByScheduleIdInOrderByScheduleIdAscDisplayOrderAscIdAsc(List.of(1L)))
                .thenAnswer(invocation -> savedHolder[0].getMatches());
        when(homeScheduleMatchPlayerRepository.findByMatchIdInOrderByMatchIdAscSideAscSlotOrderAscIdAsc(List.of(10L)))
                .thenAnswer(invocation -> savedHolder[0].getMatches().get(0).getPlayers());

        ResponseDto<AdminHomeScheduleResponse> response = homeScheduleService.createSchedule(request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getMatches()).hasSize(1);
        assertThat(response.getData().getMatches().get(0).getMapName()).isEqualTo("Neo Dark Origin");
        assertThat(response.getData().getMatches().get(0).getSideAPlayers()).hasSize(1);
        assertThat(response.getData().getMatches().get(0).getSideAPlayers().get(0).getPlayerName()).isEqualTo("login_alpha");
        assertThat(response.getData().getMatches().get(0).getSideBPlayers()).hasSize(1);
    }

    @Test
    void updateSchedule_handlesNullShadowForeignKeysInRepositoryRelations() {
        HomeScheduleEntity schedule = schedule(1L, "PROLEAGUE", "Before", HomeScheduleEntity.LINK_TYPE_DIRECT);
        AdminHomeScheduleUpdateRequest request = updateRequest();
        request.setMatches(List.of(matchRequest()));
        HomeScheduleMatchEntity repositoryMatch = match(10L, null, 1, "SET 1", "1V1", 100L);
        repositoryMatch.attachSchedule(schedule);
        HomeScheduleMatchPlayerEntity repositoryPlayer = player(1000L, null, "A", 1, 7L, null, null, null);
        repositoryPlayer.attachMatch(repositoryMatch);
        when(homeScheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(mapRepository.findAllById(any())).thenReturn(List.of(map(100L, "Neo Dark Origin")));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(7L, "login_alpha", "Real Name", "S", "PROTOSS")));
        when(homeScheduleMatchRepository.findByScheduleIdInOrderByScheduleIdAscDisplayOrderAscIdAsc(List.of(1L)))
                .thenReturn(List.of(repositoryMatch));
        when(homeScheduleMatchPlayerRepository.findByMatchIdInOrderByMatchIdAscSideAscSlotOrderAscIdAsc(List.of(10L)))
                .thenReturn(List.of(repositoryPlayer));

        ResponseDto<AdminHomeScheduleResponse> response = homeScheduleService.updateSchedule(1L, request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getMatches()).hasSize(1);
        assertThat(response.getData().getMatches().get(0).getMapName()).isEqualTo("Neo Dark Origin");
        assertThat(response.getData().getMatches().get(0).getSideAPlayers()).hasSize(1);
        assertThat(response.getData().getMatches().get(0).getSideAPlayers().get(0).getPlayerName()).isEqualTo("login_alpha");
    }

    @Test
    void updateSchedule_keepsExistingMatchesWhenRequestMatchesIsNull() {
        HomeScheduleEntity schedule = schedule(1L, "PROLEAGUE", "Before", HomeScheduleEntity.LINK_TYPE_DIRECT);
        schedule.replaceMatches(List.of(match(null, null, 1, "SET 1", "1V1", null)));
        AdminHomeScheduleUpdateRequest request = updateRequest();
        request.setMatches(null);
        when(homeScheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));

        ResponseDto<AdminHomeScheduleResponse> response = homeScheduleService.updateSchedule(1L, request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(schedule.getTitle()).isEqualTo("After");
        assertThat(schedule.getMatches()).hasSize(1);
    }

    @Test
    void updateSchedule_emptyMatchesClearsExistingMatches() {
        HomeScheduleEntity schedule = schedule(1L, "PROLEAGUE", "Before", HomeScheduleEntity.LINK_TYPE_DIRECT);
        schedule.replaceMatches(List.of(match(null, null, 1, "SET 1", "1V1", null)));
        AdminHomeScheduleUpdateRequest request = updateRequest();
        request.setMatches(List.of());
        when(homeScheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));

        ResponseDto<AdminHomeScheduleResponse> response = homeScheduleService.updateSchedule(1L, request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(schedule.getMatches()).isEmpty();
        assertThat(response.getData().getMatches()).isEmpty();
    }

    @Test
    void deleteSchedules_deduplicatesAndDeletesInAscendingOrder() {
        AdminHomeScheduleDeleteRequest request = new AdminHomeScheduleDeleteRequest();
        request.setScheduleIds(List.of(3L, 1L, 2L, 2L));
        when(homeScheduleRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        schedule(1L, "A", "A", HomeScheduleEntity.LINK_TYPE_DIRECT),
                        schedule(2L, "B", "B", HomeScheduleEntity.LINK_TYPE_DIRECT),
                        schedule(3L, "C", "C", HomeScheduleEntity.LINK_TYPE_DIRECT)
                ));
        ArgumentCaptor<HomeScheduleEntity> deleteCaptor = ArgumentCaptor.forClass(HomeScheduleEntity.class);

        ResponseDto<AdminHomeScheduleDeleteResponse> response = homeScheduleService.deleteSchedules(request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getDeletedCount()).isEqualTo(3);
        verify(homeScheduleRepository, org.mockito.Mockito.times(3)).delete(deleteCaptor.capture());
        assertThat(deleteCaptor.getAllValues())
                .extracting(HomeScheduleEntity::getId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void searchMaps_usesRepositorySearchAndClampsLimit() {
        when(mapRepository.searchByMapNameForAdmin(any(String.class), any(Pageable.class)))
                .thenReturn(List.of(map(10L, "Fighting Spirit")));

        ResponseDto<List<AdminHomeScheduleMapSearchResponse>> response = homeScheduleService.searchMaps(" fighting ", 100);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getMapName()).isEqualTo("Fighting Spirit");
    }

    @Test
    void searchProleagueTeams_returnsLiveTeamSearchResults() {
        when(proleagueTeamQueryRepository.searchLiveProleagueTeams(any(String.class), anyInt()))
                .thenReturn(List.of(AdminHomeScheduleProleagueTeamSearchResponse.builder()
                        .teamId(1L)
                        .teamName("Alpha Team")
                        .leagueId(10L)
                        .leagueName("2026 Proleague")
                        .seasonName("Season 1")
                        .build()));

        ResponseDto<List<AdminHomeScheduleProleagueTeamSearchResponse>> response =
                homeScheduleService.searchProleagueTeams(" alpha ", 100);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getTeamName()).isEqualTo("Alpha Team");
        assertThat(response.getData().get(0).getLeagueName()).isEqualTo("2026 Proleague");
    }

    private AdminHomeScheduleCreateRequest createRequest() {
        AdminHomeScheduleCreateRequest request = new AdminHomeScheduleCreateRequest();
        request.setScheduleGroup("PROLEAGUE");
        request.setTitle("Proleague draft live");
        request.setScheduledAt(LocalDateTime.now().plusHours(1));
        request.setTargetUrl("/draft/rps/12/live");
        request.setMatches(List.of(matchRequest()));
        return request;
    }

    private AdminHomeScheduleUpdateRequest updateRequest() {
        AdminHomeScheduleUpdateRequest request = new AdminHomeScheduleUpdateRequest();
        request.setScheduleGroup("PROLEAGUE");
        request.setTitle("After");
        request.setScheduledAt(LocalDateTime.now().plusHours(1));
        request.setTargetUrl("/draft/rps/12/live");
        return request;
    }

    private AdminHomeScheduleMatchRequest matchRequest() {
        AdminHomeScheduleMatchRequest match = new AdminHomeScheduleMatchRequest();
        match.setDisplayOrder(1);
        match.setSetLabel("SET 1");
        match.setMatchFormat("1V1");
        match.setTeamAName("Alpha");
        match.setTeamBName("Bravo");
        match.setMapId(100L);
        match.setPlayers(List.of(playerRequest("A", 1, 7L, null), playerRequest("B", 1, null, "external_b")));
        return match;
    }

    private AdminHomeScheduleMatchPlayerRequest playerRequest(String side, Integer slotOrder, Long userId, String playerName) {
        AdminHomeScheduleMatchPlayerRequest player = new AdminHomeScheduleMatchPlayerRequest();
        player.setSide(side);
        player.setSlotOrder(slotOrder);
        player.setUserId(userId);
        player.setPlayerName(playerName);
        player.setPlayerRank("A");
        player.setPlayerRace("TERRAN");
        return player;
    }

    private HomeScheduleEntity schedule(Long id, String scheduleGroup, String title, String linkType) {
        return HomeScheduleEntity.builder()
                .id(id)
                .scheduleGroup(scheduleGroup)
                .title(title)
                .description(title + " description")
                .scheduledAt(LocalDateTime.of(2026, 5, 17, 20, 0))
                .targetUrl("/schedules/" + id)
                .linkType(linkType)
                .displayPriority(100)
                .regDate(LocalDateTime.of(2026, 5, 17, 10, 0))
                .updateDate(LocalDateTime.of(2026, 5, 17, 10, 0))
                .build();
    }

    private HomeScheduleMatchEntity match(Long id, Long scheduleId, Integer displayOrder, String label, String format, Long mapId) {
        return HomeScheduleMatchEntity.builder()
                .id(id)
                .scheduleId(scheduleId)
                .displayOrder(displayOrder)
                .setLabel(label)
                .matchFormat(format)
                .teamAName("Alpha")
                .teamBName("Bravo")
                .mapId(mapId)
                .build();
    }

    private HomeScheduleMatchPlayerEntity player(
            Long id,
            Long matchId,
            String side,
            Integer slotOrder,
            Long userId,
            String playerName,
            String playerRank,
            String playerRace
    ) {
        return HomeScheduleMatchPlayerEntity.builder()
                .id(id)
                .matchId(matchId)
                .side(side)
                .slotOrder(slotOrder)
                .userId(userId)
                .playerName(playerName)
                .playerRank(playerRank)
                .playerRace(playerRace)
                .build();
    }

    private MapEntity map(Long id, String name) {
        return MapEntity.builder()
                .id(id)
                .mapName(name)
                .image("/maps/" + id + ".png")
                .build();
    }

    private UserEntity user(Long id, String loginId, String name, String tier, String race) {
        return UserEntity.builder()
                .id(id)
                .userId(loginId)
                .name(name)
                .tier(tier)
                .race(race)
                .build();
    }
}
