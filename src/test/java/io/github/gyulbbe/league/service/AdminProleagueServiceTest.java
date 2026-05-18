package io.github.gyulbbe.league.service;

import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.entity.DraftCandidateEntity;
import io.github.gyulbbe.draft.entity.DraftCandidateId;
import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftPickEntity;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftCandidateRepository;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import io.github.gyulbbe.draft.repository.DraftPickRepository;
import io.github.gyulbbe.draft.repository.DraftQueryRepositoryImpl;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import io.github.gyulbbe.league.dto.AdminProleagueCandidateRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueCreateRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueDraftRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueTeamMemberRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueTeamRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueTeamResponseDto;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamMemberEntity;
import io.github.gyulbbe.league.repository.LeagueParticipationRepository;
import io.github.gyulbbe.league.repository.LeagueQueryRepositoryImpl;
import io.github.gyulbbe.league.repository.LeagueRepository;
import io.github.gyulbbe.league.repository.ProleagueHistoryCleanupRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamMemberRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@Import({
        AdminProleagueService.class,
        LeagueQueryRepositoryImpl.class,
        DraftQueryRepositoryImpl.class,
        ProleagueHistoryCleanupRepository.class,
        QueryDslConfig.class
})
@EntityScan(basePackageClasses = {
        LeagueEntity.class,
        ProleagueTeamEntity.class,
        ProleagueTeamMemberEntity.class,
        LeagueParticipationEntity.class,
        DraftSessionEntity.class,
        DraftTeamEntity.class,
        DraftCandidateEntity.class,
        DraftOrderEntity.class,
        DraftPickEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        LeagueRepository.class,
        ProleagueTeamRepository.class,
        ProleagueTeamMemberRepository.class,
        LeagueParticipationRepository.class,
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        DraftCandidateRepository.class,
        DraftOrderRepository.class,
        DraftPickRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminproleaguedb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class AdminProleagueServiceTest {

    @Autowired
    private AdminProleagueService adminProleagueService;

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private DraftTeamRepository draftTeamRepository;

    @Autowired
    private DraftCandidateRepository draftCandidateRepository;

    @Autowired
    private DraftOrderRepository draftOrderRepository;

    @Autowired
    private ProleagueTeamRepository proleagueTeamRepository;

    @Autowired
    private ProleagueTeamMemberRepository proleagueTeamMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void create_without_draft_still_saves_proleague_teams() {
        Long adminId = createUser("admin01");
        createTeamUsers();

        AdminProleagueResponseDto response = adminProleagueService.createProleague(
                createRequest(false, List.of(
                        team("Alpha", "leader01", "vice01", null, 1),
                        team("Bravo", "leader02", "vice02", null, 2)
                ), List.of()),
                adminId
        );

        LeagueEntity league = leagueRepository.findById(response.getId()).orElseThrow();
        assertThat(league.getLeagueType()).isEqualTo(LeagueEntity.TYPE_PROLEAGUE);
        assertThat(response.getLeagueType()).isEqualTo(LeagueEntity.TYPE_PROLEAGUE);
        assertThat(response.getDraftSessionId()).isNull();
        assertThat(response.getTeams()).hasSize(2)
                .extracting(AdminProleagueTeamResponseDto::getTeamName)
                .containsExactly("Alpha", "Bravo");
        assertThat(response.getTeams()).extracting(AdminProleagueTeamResponseDto::getDraftTeamId)
                .containsOnlyNulls();
        assertThat(proleagueTeamRepository.countByLeagueId(response.getId())).isEqualTo(2);
        assertThat(draftSessionRepository.count()).isZero();
    }

    @Test
    void create_without_draft_saves_manual_team_members() {
        Long adminId = createUser("admin-member");
        createTeamUsers();
        createUser("member01");
        createUser("member02");
        AdminProleagueTeamRequestDto alpha = team("Alpha", "leader01", "vice01", null, 1);
        alpha.setMembers(List.of(member("member01", 1), member("member02", 2)));

        AdminProleagueResponseDto response = adminProleagueService.createProleague(
                createRequest(false, List.of(
                        alpha,
                        team("Bravo", "leader02", "vice02", null, 2)
                ), List.of()),
                adminId
        );

        assertThat(proleagueTeamMemberRepository
                .findAllByLeagueIdAndStatusOrderByDisplayOrderAscIdAsc(
                        response.getId(),
                        ProleagueTeamMemberEntity.STATUS_ACTIVE
                ))
                .extracting(ProleagueTeamMemberEntity::getUserId)
                .containsExactly(userId("member01"), userId("member02"));
        assertThat(response.getTeams()).filteredOn("teamName", "Alpha")
                .flatExtracting(AdminProleagueTeamResponseDto::getMembers)
                .extracting("userId", "source")
                .containsExactly(
                        tuple("member01", ProleagueTeamMemberEntity.SOURCE_MANUAL),
                        tuple("member02", ProleagueTeamMemberEntity.SOURCE_MANUAL)
                );
    }

    @Test
    void create_requires_proleague_type() {
        AdminProleagueCreateRequestDto missingType = createRequest(false, List.of(
                team("Alpha", "leader01", "vice01", null, 1),
                team("Bravo", "leader02", "vice02", null, 2)
        ), List.of());
        missingType.setLeagueType(null);

        assertThatThrownBy(() -> adminProleagueService.createProleague(missingType, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leagueType is required");

        AdminProleagueCreateRequestDto wrongType = createRequest(false, List.of(
                team("Alpha", "leader01", "vice01", null, 1),
                team("Bravo", "leader02", "vice02", null, 2)
        ), List.of());
        wrongType.setLeagueType(LeagueEntity.TYPE_PERSONAL);

        assertThatThrownBy(() -> adminProleagueService.createProleague(wrongType, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leagueType must be " + LeagueEntity.TYPE_PROLEAGUE);
    }

    @Test
    void create_with_draft_uses_selected_picker() {
        Long adminId = createUser("admin02");
        createTeamUsers();
        Long candidateId = createUser("candidate01");

        AdminProleagueResponseDto response = adminProleagueService.createProleague(
                createRequest(true, List.of(
                        team("Alpha", "leader01", "vice01", "vice01", 1),
                        team("Bravo", "leader02", "vice02", "leader02", 2)
                ), List.of("candidate01")),
                adminId
        );

        AdminProleagueTeamResponseDto alpha = response.getTeams().stream()
                .filter(team -> "Alpha".equals(team.getTeamName()))
                .findFirst()
                .orElseThrow();
        DraftTeamEntity alphaDraftTeam = draftTeamRepository.findById(alpha.getDraftTeamId()).orElseThrow();

        assertThat(response.getDraftSessionId()).isNotNull();
        assertThat(draftSessionRepository.findById(response.getDraftSessionId()).orElseThrow().getProleagueId())
                .isEqualTo(response.getId());
        assertThat(alphaDraftTeam.getProleagueTeamId()).isEqualTo(alpha.getId());
        assertThat(alphaDraftTeam.getPickerUserId()).isEqualTo(userId("vice01"));
        assertThat(response.getTeams()).filteredOn("teamName", "Alpha")
                .extracting(AdminProleagueTeamResponseDto::getPickerUserId)
                .containsExactly("vice01");
        assertThat(draftCandidateRepository.findById(new DraftCandidateId(response.getDraftSessionId(), candidateId)))
                .isPresent();
        assertThat(draftOrderRepository.countByDraftSessionId(response.getDraftSessionId())).isEqualTo(1);
    }

    @Test
    void create_with_picker_outside_leader_pair_fails() {
        Long adminId = createUser("admin03");
        createTeamUsers();
        createUser("candidate02");
        createUser("outsider01");

        AdminProleagueCreateRequestDto request = createRequest(true, List.of(
                team("Alpha", "leader01", "vice01", "outsider01", 1),
                team("Bravo", "leader02", "vice02", "leader02", 2)
        ), List.of("candidate02"));

        assertThatThrownBy(() -> adminProleagueService.createProleague(request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Draft picker must");
    }

    @Test
    void create_with_draft_rejects_team_leader_in_candidate_pool() {
        Long adminId = createUser("admin-candidate-block");
        createTeamUsers();

        AdminProleagueCreateRequestDto request = createRequest(true, List.of(
                team("Alpha", "leader01", "vice01", "leader01", 1),
                team("Bravo", "leader02", "vice02", "leader02", 2)
        ), List.of("leader01"));

        assertThatThrownBy(() -> adminProleagueService.createProleague(request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be draft candidates");
    }

    @Test
    void update_existing_draft_cannot_disable_draft_creation() {
        Long adminId = createUser("admin04");
        createTeamUsers();
        createUser("candidate03");
        AdminProleagueResponseDto created = adminProleagueService.createProleague(
                createRequest(true, List.of(
                        team("Alpha", "leader01", "vice01", "leader01", 1),
                        team("Bravo", "leader02", "vice02", "leader02", 2)
                ), List.of("candidate03")),
                adminId
        );

        AdminProleagueCreateRequestDto updateRequest = createRequest(false, List.of(
                team("Alpha", "leader01", "vice01", null, 1),
                team("Bravo", "leader02", "vice02", null, 2)
        ), List.of());

        assertThatThrownBy(() -> adminProleagueService.updateProleague(created.getId(), updateRequest, adminId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be removed");
    }

    @Test
    void update_requires_proleague_type() {
        Long adminId = createUser("admin06");
        createTeamUsers();
        AdminProleagueResponseDto created = adminProleagueService.createProleague(
                createRequest(false, List.of(
                        team("Alpha", "leader01", "vice01", null, 1),
                        team("Bravo", "leader02", "vice02", null, 2)
                ), List.of()),
                adminId
        );
        AdminProleagueCreateRequestDto updateRequest = createRequest(false, List.of(
                team("Alpha", "leader01", "vice01", null, 1),
                team("Bravo", "leader02", "vice02", null, 2)
        ), List.of());
        updateRequest.setLeagueType(null);

        assertThatThrownBy(() -> adminProleagueService.updateProleague(created.getId(), updateRequest, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leagueType is required");

        AdminProleagueCreateRequestDto wrongTypeRequest = createRequest(false, List.of(
                team("Alpha", "leader01", "vice01", null, 1),
                team("Bravo", "leader02", "vice02", null, 2)
        ), List.of());
        wrongTypeRequest.setLeagueType(LeagueEntity.TYPE_PERSONAL);

        assertThatThrownBy(() -> adminProleagueService.updateProleague(created.getId(), wrongTypeRequest, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leagueType must be " + LeagueEntity.TYPE_PROLEAGUE);
    }

    @Test
    void update_ready_draft_replaces_picker_and_candidates() {
        Long adminId = createUser("admin07");
        createTeamUsers();
        Long oldCandidateId = createUser("candidate04");
        Long newCandidateId = createUser("candidate05");
        AdminProleagueResponseDto created = adminProleagueService.createProleague(
                createRequest(true, List.of(
                        team("Alpha", "leader01", "vice01", "leader01", 1),
                        team("Bravo", "leader02", "vice02", "leader02", 2)
                ), List.of("candidate04")),
                adminId
        );

        AdminProleagueResponseDto updated = adminProleagueService.updateProleague(
                created.getId(),
                createRequest(true, List.of(
                        team("Alpha", "leader01", "vice01", "vice01", 1),
                        team("Bravo", "leader02", "vice02", "leader02", 2)
                ), List.of("candidate05")),
                adminId
        );
        AdminProleagueTeamResponseDto alpha = updated.getTeams().stream()
                .filter(team -> "Alpha".equals(team.getTeamName()))
                .findFirst()
                .orElseThrow();
        DraftTeamEntity alphaDraftTeam = draftTeamRepository.findById(alpha.getDraftTeamId()).orElseThrow();

        assertThat(alphaDraftTeam.getPickerUserId()).isEqualTo(userId("vice01"));
        assertThat(draftCandidateRepository.findById(new DraftCandidateId(updated.getDraftSessionId(), oldCandidateId)))
                .isNotPresent();
        assertThat(draftCandidateRepository.findById(new DraftCandidateId(updated.getDraftSessionId(), newCandidateId)))
                .isPresent();
        assertThat(draftCandidateRepository.countByDraftSessionId(updated.getDraftSessionId())).isEqualTo(1);
    }

    private AdminProleagueCreateRequestDto createRequest(
            boolean createDraft,
            List<AdminProleagueTeamRequestDto> teams,
            List<String> candidateUserIds
    ) {
        AdminProleagueCreateRequestDto request = new AdminProleagueCreateRequestDto();
        request.setLeagueName("2026 Proleague");
        request.setSeasonName("Season 1");
        request.setDescription("Proleague");
        request.setStatus("READY");
        request.setLeagueType(LeagueEntity.TYPE_PROLEAGUE);
        request.setCreateDraft(createDraft);
        request.setTeams(teams);
        if (createDraft) {
            AdminProleagueDraftRequestDto draft = new AdminProleagueDraftRequestDto();
            draft.setTeamCount(teams.size());
            draft.setPickTimeSeconds(30);
            draft.setOrderMode("BASIC");
            draft.setCandidates(candidateUserIds.stream().map(this::candidate).toList());
            request.setDraft(draft);
        }
        return request;
    }

    private AdminProleagueTeamRequestDto team(
            String teamName,
            String leaderUserId,
            String viceLeaderUserId,
            String pickerUserId,
            int displayOrder
    ) {
        AdminProleagueTeamRequestDto team = new AdminProleagueTeamRequestDto();
        team.setTeamName(teamName);
        team.setLeaderUserId(leaderUserId);
        team.setViceLeaderUserId(viceLeaderUserId);
        team.setPickerUserId(pickerUserId);
        team.setDisplayOrder(displayOrder);
        return team;
    }

    private AdminProleagueCandidateRequestDto candidate(String userId) {
        AdminProleagueCandidateRequestDto candidate = new AdminProleagueCandidateRequestDto();
        candidate.setUserId(userId);
        return candidate;
    }

    private AdminProleagueTeamMemberRequestDto member(String userId, int displayOrder) {
        AdminProleagueTeamMemberRequestDto member = new AdminProleagueTeamMemberRequestDto();
        member.setUserId(userId);
        member.setDisplayOrder(displayOrder);
        return member;
    }

    private void createTeamUsers() {
        createUser("leader01");
        createUser("vice01");
        createUser("leader02");
        createUser("vice02");
    }

    private Long createUser(String userId) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(userId)
                .status("ACTIVE")
                .userType("ROLE_USER")
                .race("TERRAN")
                .build();
        return userRepository.save(user).getId();
    }

    private Long userId(String loginId) {
        return userRepository.findByUserIdIgnoreCase(loginId).getId();
    }
}
