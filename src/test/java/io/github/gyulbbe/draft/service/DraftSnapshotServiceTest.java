package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftLiveSnapshotResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderRequestDto;
import io.github.gyulbbe.draft.dto.DraftPickRequestDto;
import io.github.gyulbbe.draft.dto.DraftSessionRequestDto;
import io.github.gyulbbe.draft.dto.DraftTeamRequestDto;
import io.github.gyulbbe.draft.entity.DraftCandidateEntity;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        DraftQueryRepositoryImpl.class,
        DraftLiveSessionTracker.class,
        DraftService.class,
        DraftPermissionService.class,
        DraftAdminService.class,
        DraftSnapshotService.class
})
@EntityScan(basePackageClasses = {
        DraftSessionEntity.class,
        DraftTeamEntity.class,
        DraftCandidateEntity.class,
        DraftOrderEntity.class,
        DraftPickEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        DraftCandidateRepository.class,
        DraftOrderRepository.class,
        DraftPickRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:draftsnapshotdb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DraftSnapshotServiceTest {

    @Autowired
    private DraftService draftService;

    @Autowired
    private DraftAdminService draftAdminService;

    @Autowired
    private DraftSnapshotService draftSnapshotService;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void snapshot_includes_turn_roster_candidates_and_picker_permissions() {
        AuthActor owner = createActor("owner01", "Owner One", "ROLE_USER");
        AuthActor pickerA = createActor("picker01", "Picker One", "ROLE_USER");
        AuthActor pickerB = createActor("picker02", "Picker Two", "ROLE_USER");
        Long candidate1Id = createUser("candidate01", "Candidate One", "ROLE_USER", "S", "ZERG");
        Long candidate2Id = createUser("candidate02", "Candidate Two", "ROLE_USER", "A", "TERRAN");

        Long sessionId = createSession(owner, "Snapshot Session");
        Long teamAId = createTeam(owner, sessionId, "Red", 1);
        Long teamBId = createTeam(owner, sessionId, "Blue", 2);
        assignPicker(owner, teamAId, pickerA.userPk());
        assignPicker(owner, teamBId, pickerB.userPk());
        createCandidate(owner, sessionId, candidate1Id, "Candidate One", "ZERG");
        createCandidate(owner, sessionId, candidate2Id, "Candidate Two", "TERRAN");
        createOrder(owner, sessionId, 1L, teamAId);
        createOrder(owner, sessionId, 2L, teamBId);
        updateSession(owner, sessionId, "LIVE", 1, teamAId, LocalDateTime.now().plusSeconds(30));
        createPick(owner, sessionId, 1L, teamAId, candidate1Id, pickerA.userPk());

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, pickerB);

        assertThat(snapshot.getSession().getId()).isEqualTo(sessionId);
        assertThat(snapshot.getCurrentTurn().getPickNo()).isEqualTo(2L);
        assertThat(snapshot.getCurrentTurn().getTeamId()).isEqualTo(teamBId);
        assertThat(snapshot.getTeams()).hasSize(2);
        var roster = snapshot.getTeams().stream()
                .filter(team -> team.getId().equals(teamAId))
                .findFirst()
                .orElseThrow()
                .getRoster();
        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).getPickNo()).isEqualTo(1L);
        assertThat(roster.get(0).getRoundNo()).isEqualTo(1L);
        assertThat(roster.get(0).getCandidateUserId()).isEqualTo(candidate1Id);
        assertThat(roster.get(0).getCandidateName()).isEqualTo("candidate01");
        assertThat(roster.get(0).getTier()).isEqualTo("S");
        assertThat(roster.get(0).getRace()).isEqualTo("ZERG");
        assertThat(snapshot.getAvailableCandidates()).hasSize(1);
        assertThat(snapshot.getAvailableCandidates().get(0).getCandidateName()).isEqualTo("candidate02");
        assertThat(snapshot.getAvailableCandidates().get(0).getTier()).isEqualTo("A");
        assertThat(snapshot.getAvailableCandidates().get(0).getRace()).isEqualTo("TERRAN");
        assertThat(snapshot.getPickedCandidates()).hasSize(1);
        assertThat(snapshot.getPickedCandidates().get(0).getCandidateName()).isEqualTo("candidate01");
        assertThat(snapshot.getPickedCandidates().get(0).getTier()).isEqualTo("S");
        assertThat(snapshot.getPickedCandidates().get(0).getRace()).isEqualTo("ZERG");
        assertThat(snapshot.getRecentPicks()).hasSize(1);
        assertThat(snapshot.getRecentPicks().get(0).getCandidateName()).isEqualTo("candidate01");
        assertThat(snapshot.getRecentPicks().get(0).getTier()).isEqualTo("S");
        assertThat(snapshot.getRecentPicks().get(0).getRace()).isEqualTo("ZERG");
        assertThat(snapshot.getPermissions().getMyTeamId()).isEqualTo(teamBId);
        assertThat(snapshot.getPermissions().isCanPick()).isTrue();
    }

    @Test
    void snapshot_sets_canControl_true_for_owner_and_owner_cannot_pick_without_picker_role() {
        AuthActor owner = createActor("owner02", "Owner Two", "ROLE_USER");
        AuthActor picker = createActor("picker03", "Picker Three", "ROLE_USER");
        Long candidateId = createUser("candidate03", "Candidate Three", "ROLE_USER");

        Long sessionId = createSession(owner, "Owner Permission Session");
        Long teamId = createTeam(owner, sessionId, "Alpha", 1);
        assignPicker(owner, teamId, picker.userPk());
        createCandidate(owner, sessionId, candidateId, "Candidate Three", "PROTOSS");
        createOrder(owner, sessionId, 1L, teamId);
        updateSession(owner, sessionId, "LIVE", 1, teamId, LocalDateTime.now().plusSeconds(25));

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, owner);

        assertThat(snapshot.getPermissions().isCanControl()).isTrue();
        assertThat(snapshot.getPermissions().isCanPick()).isFalse();
        assertThat(snapshot.getPermissions().getMyTeamId()).isNull();
    }

    @Test
    void snapshot_sets_canControl_true_for_admin_on_foreign_session() {
        AuthActor owner = createActor("owner03", "Owner Three", "ROLE_USER");
        AuthActor admin = createActor("admin01", "Admin One", "ROLE_ADMIN");
        Long candidateId = createUser("candidate04", "Candidate Four", "ROLE_USER");

        Long sessionId = createSession(owner, "Admin Permission Session");
        Long teamId = createTeam(owner, sessionId, "Admin Team", 1);
        createCandidate(owner, sessionId, candidateId, "Candidate Four", "ZERG");
        createOrder(owner, sessionId, 1L, teamId);
        updateSession(owner, sessionId, "LIVE", 1, teamId, LocalDateTime.now().plusSeconds(20));

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, admin);

        assertThat(snapshot.getPermissions().isCanControl()).isTrue();
        assertThat(snapshot.getPermissions().isCanPick()).isFalse();
    }

    @Test
    void snapshot_allows_anonymous_view_with_no_permissions() {
        AuthActor owner = createActor("owner04", "Owner Four", "ROLE_USER");
        Long candidateId = createUser("candidate05", "Candidate Five", "ROLE_USER");

        Long sessionId = createSession(owner, "Anonymous Snapshot Session");
        Long teamId = createTeam(owner, sessionId, "Anon Team", 1);
        createCandidate(owner, sessionId, candidateId, "Candidate Five", "RANDOM");
        createOrder(owner, sessionId, 1L, teamId);
        updateSession(owner, sessionId, "LIVE", 1, teamId, LocalDateTime.now().plusSeconds(25));

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, null);

        assertThat(snapshot.getSession().getId()).isEqualTo(sessionId);
        assertThat(snapshot.getPermissions().isCanControl()).isFalse();
        assertThat(snapshot.getPermissions().isCanPick()).isFalse();
    }

    @Test
    void snapshot_uses_order_based_current_turn_when_current_team_is_null() {
        AuthActor owner = createActor("owner05", "Owner Five", "ROLE_USER");
        AuthActor picker = createActor("picker04", "Picker Four", "ROLE_USER");
        Long candidateId = createUser("candidate06", "Candidate Six", "ROLE_USER");

        Long sessionId = createSession(owner, "Order Snapshot Session");
        Long teamId = createTeam(owner, sessionId, "Order Team", 1);
        assignPicker(owner, teamId, picker.userPk());
        createCandidate(owner, sessionId, candidateId, "Candidate Six", "ZERG");
        createOrder(owner, sessionId, 2L, teamId);

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.update(
                session.getTitle(),
                "LIVE",
                session.getTeamCount(),
                session.getPickTimeSeconds(),
                2,
                null,
                LocalDateTime.now().plusSeconds(25),
                LocalDateTime.now().minusMinutes(1),
                null
        );

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, picker);

        assertThat(snapshot.getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(snapshot.getSession().getCurrentDraftTeamId()).isNull();
        assertThat(snapshot.getCurrentTurn()).isNotNull();
        assertThat(snapshot.getCurrentTurn().getPickNo()).isEqualTo(2L);
        assertThat(snapshot.getCurrentTurn().getTeamId()).isEqualTo(teamId);
        assertThat(snapshot.getPermissions().getMyTeamId()).isEqualTo(teamId);
        assertThat(snapshot.getPermissions().isCanPick()).isTrue();
    }

    private Long createSession(AuthActor actor, String title) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle(title);
        requestDto.setStatus("READY");
        requestDto.setTeamCount(2);
        requestDto.setPickTimeSeconds(30);
        requestDto.setCurrentPickNo(1);
        return draftService.createSession(requestDto, actor).getData().getId();
    }

    private Long createTeam(AuthActor actor, Long sessionId, String teamName, int displayOrder) {
        DraftTeamRequestDto requestDto = new DraftTeamRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setTeamName(teamName);
        requestDto.setDisplayOrder(displayOrder);
        return draftService.createTeam(requestDto, actor).getData().getId();
    }

    private void assignPicker(AuthActor actor, Long teamId, Long pickerUserId) {
        draftAdminService.assignPicker(teamId, pickerUserId, actor);
    }

    private void createCandidate(AuthActor actor, Long sessionId, Long candidateUserId, String candidateName, String race) {
        DraftCandidateRequestDto requestDto = new DraftCandidateRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setCandidateUserId(candidateUserId);
        requestDto.setCandidateName(candidateName);
        requestDto.setRace(race);
        requestDto.setStatus("WAITING");
        draftService.createCandidate(requestDto, actor);
    }

    private void createOrder(AuthActor actor, Long sessionId, Long pickNo, Long teamId) {
        DraftOrderRequestDto requestDto = new DraftOrderRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setDraftTeamId(teamId);
        draftService.createOrder(requestDto, actor);
    }

    private void updateSession(AuthActor actor, Long sessionId, String status, Integer currentPickNo, Long currentTeamId, LocalDateTime deadlineAt) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setStatus(status);
        requestDto.setCurrentPickNo(currentPickNo);
        requestDto.setCurrentDraftTeamId(currentTeamId);
        requestDto.setDeadlineAt(deadlineAt);
        draftService.updateSession(sessionId, requestDto, actor);
    }

    private void createPick(AuthActor actor, Long sessionId, Long pickNo, Long teamId, Long candidateUserId, Long pickedByUserId) {
        DraftPickRequestDto requestDto = new DraftPickRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setDraftTeamId(teamId);
        requestDto.setCandidateUserId(candidateUserId);
        requestDto.setPickedByUserId(pickedByUserId);
        draftService.createPick(requestDto, actor);
    }

    private AuthActor createActor(String userId, String name, String role) {
        Long userPk = createUser(userId, name, role);
        return new AuthActor(userPk, userId, role);
    }

    private Long createUser(String userId, String name, String role) {
        return createUser(userId, name, role, null, null);
    }

    private Long createUser(String userId, String name, String role, String tier, String race) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .tier(tier)
                .race(race)
                .status("ACTIVE")
                .userType(role)
                .build();
        return userRepository.save(user).getId();
    }
}
