package io.github.gyulbbe.league.service;

import io.github.gyulbbe.league.dto.AdminPersonalLeagueCreateRequestDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeaguePlayerRequestDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeagueResponseDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeagueTournamentRequestDto;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.repository.LeagueParticipationRepository;
import io.github.gyulbbe.league.repository.LeagueRepository;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchScoreSubmissionEntity;
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
import io.github.gyulbbe.tournament.service.TournamentBracketProgressionService;
import io.github.gyulbbe.tournament.service.TournamentCreationService;
import io.github.gyulbbe.tournament.service.TournamentService;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DataJpaTest
@Import({
        AdminPersonalLeagueService.class,
        TournamentCreationService.class,
        TournamentBracketProgressionService.class
})
@EntityScan(basePackageClasses = {
        LeagueEntity.class,
        LeagueParticipationEntity.class,
        TournamentEntity.class,
        TournamentParticipantEntity.class,
        TournamentStageEntity.class,
        TournamentGroupEntity.class,
        TournamentGroupEntryEntity.class,
        TournamentMatchEntity.class,
        TournamentMatchSlotEntity.class,
        TournamentRouteEntity.class,
        TournamentResultSlotEntity.class,
        TournamentMatchScoreSubmissionEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        LeagueRepository.class,
        LeagueParticipationRepository.class,
        TournamentRepository.class,
        TournamentParticipantRepository.class,
        TournamentStageRepository.class,
        TournamentGroupRepository.class,
        TournamentGroupEntryRepository.class,
        TournamentMatchRepository.class,
        TournamentMatchSlotRepository.class,
        TournamentRouteRepository.class,
        TournamentResultSlotRepository.class,
        TournamentMatchScoreSubmissionRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminpersonalleaguedb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AdminPersonalLeagueServiceTest {

    @Autowired
    private AdminPersonalLeagueService adminPersonalLeagueService;

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private LeagueParticipationRepository leagueParticipationRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentParticipantRepository tournamentParticipantRepository;

    @Autowired
    private TournamentStageRepository tournamentStageRepository;

    @Autowired
    private TournamentGroupRepository tournamentGroupRepository;

    @Autowired
    private TournamentMatchRepository tournamentMatchRepository;

    @Autowired
    private TournamentMatchSlotRepository tournamentMatchSlotRepository;

    @Autowired
    private TournamentMatchScoreSubmissionRepository scoreSubmissionRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private TournamentService tournamentService;

    @BeforeEach
    void setUp() {
        given(tournamentService.buildDetail(any(TournamentEntity.class)))
                .willAnswer(invocation -> {
                    TournamentEntity tournament = invocation.getArgument(0);
                    return TournamentDetailResponseDto.builder().id(tournament.getId()).build();
                });
    }

    @Test
    void create_without_tournament_saves_personal_league_participations_only() {
        Long adminId = createUser("admin01", "ACTIVE");
        createUser("player01", "ACTIVE");
        createUser("player02", "ACTIVE");

        AdminPersonalLeagueResponseDto response = adminPersonalLeagueService.createPersonalLeague(
                request(false, null, "player01", "player02"),
                adminId
        );

        LeagueEntity league = leagueRepository.findById(response.getId()).orElseThrow();
        assertThat(league.getLeagueType()).isEqualTo(LeagueEntity.TYPE_PERSONAL);
        assertThat(response.getLeagueType()).isEqualTo(LeagueEntity.TYPE_PERSONAL);
        assertThat(response.getTournamentId()).isNull();
        assertThat(leagueParticipationRepository.countByLeagueId(response.getId())).isEqualTo(2);
        assertThat(tournamentRepository.count()).isZero();
    }

    @Test
    void create_requires_personal_league_type() {
        AdminPersonalLeagueCreateRequestDto missingType = request(false, null, "player01", "player02");
        missingType.setLeagueType(null);

        assertThatThrownBy(() -> adminPersonalLeagueService.createPersonalLeague(missingType, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leagueType is required");

        AdminPersonalLeagueCreateRequestDto wrongType = request(false, null, "player01", "player02");
        wrongType.setLeagueType(LeagueEntity.TYPE_PROLEAGUE);

        assertThatThrownBy(() -> adminPersonalLeagueService.createPersonalLeague(wrongType, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leagueType must be " + LeagueEntity.TYPE_PERSONAL);
    }

    @Test
    void create_single_tournament_places_players_by_input_order() {
        Long adminId = createUser("admin02", "ACTIVE");
        Long playerOne = createUser("player03", "ACTIVE");
        Long playerTwo = createUser("player04", "ACTIVE");
        Long playerThree = createUser("player05", "ACTIVE");

        AdminPersonalLeagueResponseDto response = adminPersonalLeagueService.createPersonalLeague(
                request(true, TournamentStageEntity.TYPE_SINGLE_ELIMINATION, "player03", "player04", "player05"),
                adminId
        );

        assertThat(response.getTournamentId()).isNotNull();
        assertThat(tournamentParticipantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(response.getTournamentId()))
                .extracting(TournamentParticipantEntity::getUserId)
                .containsExactly(playerOne, playerTwo, playerThree);
        assertThat(response.getTournamentBracketType()).isEqualTo(TournamentStageEntity.TYPE_SINGLE_ELIMINATION);
        assertThat(response.getTournamentBestOf()).isEqualTo(3);
    }

    @Test
    void create_dual_tournament_chunks_players_by_four_and_leaves_bye_slots() {
        Long adminId = createUser("admin03", "ACTIVE");
        createUser("player06", "ACTIVE");
        createUser("player07", "ACTIVE");
        createUser("player08", "ACTIVE");
        createUser("player09", "ACTIVE");
        createUser("player10", "ACTIVE");

        AdminPersonalLeagueResponseDto response = adminPersonalLeagueService.createPersonalLeague(
                request(true, TournamentStageEntity.TYPE_DUAL_GROUP,
                        "player06", "player07", "player08", "player09", "player10"),
                adminId
        );

        TournamentStageEntity stage = tournamentStageRepository
                .findAllByTournamentIdOrderByDisplayOrderAsc(response.getTournamentId())
                .get(0);
        List<TournamentGroupEntity> groups = tournamentGroupRepository.findAllByStageIdOrderByDisplayOrderAsc(stage.getId());
        TournamentGroupEntity bGroup = groups.stream()
                .filter(group -> "B".equals(group.getGroupCode()))
                .findFirst()
                .orElseThrow();
        List<Long> bMatchIds = tournamentMatchRepository.findAllByGroupIdOrderByDisplayOrderAsc(bGroup.getId())
                .stream()
                .map(TournamentMatchEntity::getId)
                .toList();

        assertThat(groups).extracting(TournamentGroupEntity::getGroupCode).containsExactly("A", "B");
        assertThat(tournamentParticipantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(response.getTournamentId()))
                .hasSize(5);
        assertThat(tournamentMatchSlotRepository.findAllByMatchIdInOrderBySlotNoAsc(bMatchIds))
                .filteredOn(slot -> Integer.valueOf(1).equals(slot.getIsBye()))
                .hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void create_rejects_duplicate_or_inactive_players() {
        Long adminId = createUser("admin04", "ACTIVE");
        createUser("player11", "ACTIVE");
        createUser("player12", "INACTIVE");

        assertThatThrownBy(() -> adminPersonalLeagueService.createPersonalLeague(
                request(false, null, "player11", "PLAYER11"),
                adminId
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");

        assertThatThrownBy(() -> adminPersonalLeagueService.createPersonalLeague(
                request(false, null, "player11", "player12"),
                adminId
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE player");
    }

    @Test
    void update_existing_tournament_cannot_disable_tournament() {
        Long adminId = createUser("admin05", "ACTIVE");
        createUser("player13", "ACTIVE");
        createUser("player14", "ACTIVE");
        AdminPersonalLeagueResponseDto created = adminPersonalLeagueService.createPersonalLeague(
                request(true, TournamentStageEntity.TYPE_SINGLE_ELIMINATION, "player13", "player14"),
                adminId
        );

        assertThatThrownBy(() -> adminPersonalLeagueService.updatePersonalLeague(
                created.getId(),
                request(false, null, "player13", "player14"),
                adminId
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be removed");
    }

    @Test
    void update_requires_personal_league_type() {
        Long adminId = createUser("admin06", "ACTIVE");
        createUser("player15", "ACTIVE");
        createUser("player16", "ACTIVE");
        AdminPersonalLeagueResponseDto created = adminPersonalLeagueService.createPersonalLeague(
                request(false, null, "player15", "player16"),
                adminId
        );
        AdminPersonalLeagueCreateRequestDto updateRequest = request(false, null, "player15", "player16");
        updateRequest.setLeagueType(null);

        assertThatThrownBy(() -> adminPersonalLeagueService.updatePersonalLeague(created.getId(), updateRequest, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leagueType is required");

        AdminPersonalLeagueCreateRequestDto wrongTypeRequest = request(false, null, "player15", "player16");
        wrongTypeRequest.setLeagueType(LeagueEntity.TYPE_PROLEAGUE);

        assertThatThrownBy(() -> adminPersonalLeagueService.updatePersonalLeague(created.getId(), wrongTypeRequest, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leagueType must be " + LeagueEntity.TYPE_PERSONAL);
    }

    @Test
    void update_unprogressed_tournament_replaces_graph_but_keeps_tournament_id() {
        Long adminId = createUser("admin07", "ACTIVE");
        createUser("player17", "ACTIVE");
        createUser("player18", "ACTIVE");
        Long updatedOne = createUser("player19", "ACTIVE");
        Long updatedTwo = createUser("player20", "ACTIVE");
        Long updatedThree = createUser("player21", "ACTIVE");
        AdminPersonalLeagueResponseDto created = adminPersonalLeagueService.createPersonalLeague(
                request(true, TournamentStageEntity.TYPE_SINGLE_ELIMINATION, "player17", "player18"),
                adminId
        );

        AdminPersonalLeagueResponseDto updated = adminPersonalLeagueService.updatePersonalLeague(
                created.getId(),
                request(true, TournamentStageEntity.TYPE_SINGLE_ELIMINATION, "player19", "player20", "player21"),
                adminId
        );

        assertThat(updated.getTournamentId()).isEqualTo(created.getTournamentId());
        assertThat(tournamentParticipantRepository.findAllByTournamentIdOrderBySeedNoAscIdAsc(updated.getTournamentId()))
                .extracting(TournamentParticipantEntity::getUserId)
                .containsExactly(updatedOne, updatedTwo, updatedThree);
    }

    @Test
    void update_progressed_tournament_fails() {
        Long adminId = createUser("admin08", "ACTIVE");
        createUser("player22", "ACTIVE");
        createUser("player23", "ACTIVE");
        AdminPersonalLeagueResponseDto created = adminPersonalLeagueService.createPersonalLeague(
                request(true, TournamentStageEntity.TYPE_SINGLE_ELIMINATION, "player22", "player23"),
                adminId
        );
        TournamentStageEntity stage = tournamentStageRepository
                .findAllByTournamentIdOrderByDisplayOrderAsc(created.getTournamentId())
                .get(0);
        TournamentMatchEntity match = tournamentMatchRepository.findAllByStageIdOrderByDisplayOrderAsc(stage.getId()).get(0);
        scoreSubmissionRepository.save(TournamentMatchScoreSubmissionEntity.builder()
                .tournamentId(created.getTournamentId())
                .matchId(match.getId())
                .submittedByUserId(adminId)
                .submitterRole(TournamentMatchScoreSubmissionEntity.ROLE_ADMIN)
                .slot1Score(1)
                .slot2Score(0)
                .winnerSlotNo(1)
                .status(TournamentMatchScoreSubmissionEntity.STATUS_PENDING)
                .build());

        assertThatThrownBy(() -> adminPersonalLeagueService.updatePersonalLeague(
                created.getId(),
                request(true, TournamentStageEntity.TYPE_SINGLE_ELIMINATION, "player22", "player23"),
                adminId
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("progressed");
    }

    private AdminPersonalLeagueCreateRequestDto request(
            boolean createTournament,
            String bracketType,
            String... playerLoginIds
    ) {
        AdminPersonalLeagueCreateRequestDto request = new AdminPersonalLeagueCreateRequestDto();
        request.setLeagueName("2026 Personal League");
        request.setSeasonName("Season 1");
        request.setDescription("Personal league");
        request.setStatus("READY");
        request.setLeagueType(LeagueEntity.TYPE_PERSONAL);
        request.setCreateTournament(createTournament);
        request.setPlayers(List.of(playerLoginIds).stream().map(this::player).toList());
        if (createTournament) {
            AdminPersonalLeagueTournamentRequestDto tournament = new AdminPersonalLeagueTournamentRequestDto();
            tournament.setBracketType(bracketType);
            tournament.setBestOf(3);
            request.setTournament(tournament);
        }
        return request;
    }

    private AdminPersonalLeaguePlayerRequestDto player(String userId) {
        AdminPersonalLeaguePlayerRequestDto player = new AdminPersonalLeaguePlayerRequestDto();
        player.setUserId(userId);
        return player;
    }

    private Long createUser(String userId, String status) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(userId)
                .status(status)
                .userType("ROLE_USER")
                .race("TERRAN")
                .build();
        return userRepository.save(user).getId();
    }
}
