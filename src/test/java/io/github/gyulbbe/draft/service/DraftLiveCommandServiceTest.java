package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftLiveSnapshotResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderRequestDto;
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
import io.github.gyulbbe.draft.ws.DraftEventPublisher;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        DraftQueryRepositoryImpl.class,
        DraftLiveSessionTracker.class,
        DraftOrderPatternService.class,
        DraftService.class,
        DraftPermissionService.class,
        DraftAdminService.class,
        DraftSnapshotService.class,
        DraftEventPublisher.class,
        DraftLivePreviewRelayService.class,
        DraftLiveCommandService.class
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
        "spring.datasource.url=jdbc:h2:mem:draftlivecommanddb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DraftLiveCommandServiceTest {

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private DraftService draftService;

    @Autowired
    private DraftAdminService draftAdminService;

    @Autowired
    private DraftLiveCommandService draftLiveCommandService;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private DraftOrderRepository draftOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void owner_can_start_pause_resume_and_finish_session() {
        AuthActor owner = createActor("owner01", "Owner One", "ROLE_USER");
        Long sessionId = createSession(owner, "Owner Live Session");
        Long teamAId = createTeam(owner, sessionId, "A", 1);
        createTeam(owner, sessionId, "B", 2);
        createOrder(owner, sessionId, 1L, teamAId);

        DraftLiveSnapshotResponseDto started = draftLiveCommandService.startSession(sessionId, owner);
        DraftLiveSnapshotResponseDto paused = draftLiveCommandService.pauseSession(sessionId, owner);
        DraftLiveSnapshotResponseDto resumed = draftLiveCommandService.resumeSession(sessionId, owner, 15);
        DraftLiveSnapshotResponseDto finished = draftLiveCommandService.finishSession(sessionId, owner, "manual");

        assertThat(started.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(started.getSession().getCurrentDraftTeamId()).isEqualTo(teamAId);
        assertThat(paused.getSession().getStatus()).isEqualTo("PAUSED");
        assertThat(resumed.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(finished.getSession().getStatus()).isEqualTo("FINISHED");
    }

    @Test
    void admin_can_control_foreign_session() {
        AuthActor owner = createActor("owner02", "Owner Two", "ROLE_USER");
        AuthActor admin = createActor("admin01", "Admin One", "ROLE_ADMIN");
        Long sessionId = createSession(owner, "Admin Controlled Session");
        Long teamAId = createTeam(owner, sessionId, "Alpha", 1);
        createOrder(owner, sessionId, 1L, teamAId);

        DraftLiveSnapshotResponseDto started = draftLiveCommandService.startSession(sessionId, admin);
        DraftLiveSnapshotResponseDto paused = draftLiveCommandService.pauseSession(sessionId, admin);

        assertThat(started.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(paused.getSession().getStatus()).isEqualTo("PAUSED");
    }

    @Test
    void non_owner_non_admin_cannot_control_foreign_session() {
        AuthActor owner = createActor("owner03", "Owner Three", "ROLE_USER");
        AuthActor otherUser = createActor("other01", "Other One", "ROLE_USER");
        Long sessionId = createSession(owner, "Forbidden Session");
        Long teamAId = createTeam(owner, sessionId, "Alpha", 1);
        createOrder(owner, sessionId, 1L, teamAId);

        assertThatThrownBy(() -> draftLiveCommandService.startSession(sessionId, otherUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("session owner or an administrator");
    }

    @Test
    void owner_can_force_skip_but_owner_cannot_pick_without_picker_role() {
        AuthActor owner = createActor("owner04", "Owner Four", "ROLE_USER");
        AuthActor pickerA = createActor("picker01", "Picker One", "ROLE_USER");
        AuthActor pickerB = createActor("picker02", "Picker Two", "ROLE_USER");
        Long candidate1Id = createUser("candidate01", "Candidate One", "ROLE_USER");
        Long candidate2Id = createUser("candidate02", "Candidate Two", "ROLE_USER");

        Long sessionId = createSession(owner, "Force Skip Session");
        Long teamAId = createTeam(owner, sessionId, "Red", 1);
        Long teamBId = createTeam(owner, sessionId, "Blue", 2);
        assignPicker(owner, teamAId, pickerA.userPk());
        assignPicker(owner, teamBId, pickerB.userPk());
        createCandidate(owner, sessionId, candidate1Id, "Candidate One", "ZERG");
        createCandidate(owner, sessionId, candidate2Id, "Candidate Two", "PROTOSS");
        createOrder(owner, sessionId, 1L, teamAId);
        createOrder(owner, sessionId, 2L, teamBId);
        draftLiveCommandService.startSession(sessionId, owner);

        assertThatThrownBy(() -> draftLiveCommandService.pick(sessionId, candidate1Id, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the picker");

        DraftLiveSnapshotResponseDto skipped = draftLiveCommandService.forceSkip(sessionId, owner, "owner-skip");

        assertThat(skipped.getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(skipped.getSession().getCurrentDraftTeamId()).isEqualTo(teamBId);
    }

    @Test
    void picker_can_pick_and_session_advances() {
        AuthActor owner = createActor("owner05", "Owner Five", "ROLE_USER");
        AuthActor pickerA = createActor("picker03", "Picker Three", "ROLE_USER");
        AuthActor pickerB = createActor("picker04", "Picker Four", "ROLE_USER");
        Long candidate1Id = createUser("candidate03", "Candidate Three", "ROLE_USER");
        Long candidate2Id = createUser("candidate04", "Candidate Four", "ROLE_USER");

        Long sessionId = createSession(owner, "Picking Session");
        Long teamAId = createTeam(owner, sessionId, "Red", 1);
        Long teamBId = createTeam(owner, sessionId, "Blue", 2);
        assignPicker(owner, teamAId, pickerA.userPk());
        assignPicker(owner, teamBId, pickerB.userPk());
        createCandidate(owner, sessionId, candidate1Id, "Candidate Three", "ZERG");
        createCandidate(owner, sessionId, candidate2Id, "Candidate Four", "TERRAN");
        createOrder(owner, sessionId, 1L, teamAId);
        createOrder(owner, sessionId, 2L, teamBId);
        draftLiveCommandService.startSession(sessionId, owner);

        DraftLiveSnapshotResponseDto result = draftLiveCommandService.pick(sessionId, candidate1Id, pickerA);

        assertThat(result.getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(result.getSession().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(result.getPickedCandidates()).hasSize(1);
        assertThat(result.getRecentPicks()).hasSize(1);
    }

    @Test
    void force_skip_at_last_existing_order_keeps_live_when_waiting_candidates_remain() {
        AuthActor owner = createActor("owner-skip-last", "Owner Skip Last", "ROLE_USER");
        Long candidate1Id = createUser("candidate-skip-last-1", "Candidate Skip Last One", "ROLE_USER");
        Long candidate2Id = createUser("candidate-skip-last-2", "Candidate Skip Last Two", "ROLE_USER");
        Long candidate3Id = createUser("candidate-skip-last-3", "Candidate Skip Last Three", "ROLE_USER");

        Long sessionId = createSession(owner, "Force Skip Last Order Session");
        Long teamAId = createTeam(owner, sessionId, "Red Skip Last", 1);
        Long teamBId = createTeam(owner, sessionId, "Blue Skip Last", 2);
        createCandidate(owner, sessionId, candidate1Id, "Candidate Skip Last One", "ZERG");
        createCandidate(owner, sessionId, candidate2Id, "Candidate Skip Last Two", "PROTOSS");
        createCandidate(owner, sessionId, candidate3Id, "Candidate Skip Last Three", "TERRAN");
        createOrder(owner, sessionId, 1L, teamAId);
        createOrder(owner, sessionId, 2L, teamBId);
        draftLiveCommandService.startSession(sessionId, owner);
        draftLiveCommandService.forceSkip(sessionId, owner, "first skip");

        DraftLiveSnapshotResponseDto skipped = draftLiveCommandService.forceSkip(sessionId, owner, "last configured order skip");

        assertThat(skipped.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(skipped.getSession().getCurrentPickNo()).isEqualTo(3);
        assertThat(skipped.getSession().getCurrentDraftTeamId()).isEqualTo(teamAId);
        assertThat(skipped.getAvailableCandidates()).hasSize(3);
        assertThat(draftOrderRepository.findByDraftSessionIdAndPickNo(sessionId, 3L)).isPresent()
                .get()
                .extracting(DraftOrderEntity::getDraftTeamId)
                .isEqualTo(teamAId);
    }

    @Test
    void force_skip_extends_snake_order_by_next_pick_no() {
        AuthActor owner = createActor("owner-snake-skip", "Owner Snake Skip", "ROLE_USER");
        Long candidateId = createUser("candidate-snake-skip", "Candidate Snake Skip", "ROLE_USER");

        Long sessionId = createSession(owner, "Snake Force Skip Session", 2, "SNAKE");
        Long teamAId = createTeam(owner, sessionId, "Snake A", 1);
        Long teamBId = createTeam(owner, sessionId, "Snake B", 2);
        createCandidate(owner, sessionId, candidateId, "Candidate Snake Skip", "ZERG");
        createOrder(owner, sessionId, 1L, teamAId);
        createOrder(owner, sessionId, 2L, teamBId);
        createOrder(owner, sessionId, 3L, teamBId);
        createOrder(owner, sessionId, 4L, teamAId);
        createOrder(owner, sessionId, 5L, teamAId);
        draftLiveCommandService.startSession(sessionId, owner);

        draftLiveCommandService.forceSkip(sessionId, owner, "skip to 2");
        draftLiveCommandService.forceSkip(sessionId, owner, "skip to 3");
        draftLiveCommandService.forceSkip(sessionId, owner, "skip to 4");
        DraftLiveSnapshotResponseDto skipTo5 = draftLiveCommandService.forceSkip(sessionId, owner, "skip to 5");
        DraftLiveSnapshotResponseDto skipTo6 = draftLiveCommandService.forceSkip(sessionId, owner, "skip to 6");
        DraftLiveSnapshotResponseDto skipTo7 = draftLiveCommandService.forceSkip(sessionId, owner, "skip to 7");
        DraftLiveSnapshotResponseDto skipTo8 = draftLiveCommandService.forceSkip(sessionId, owner, "skip to 8");

        assertThat(skipTo5.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(skipTo5.getSession().getCurrentPickNo()).isEqualTo(5);
        assertThat(skipTo5.getSession().getCurrentDraftTeamId()).isEqualTo(teamAId);
        assertThat(skipTo6.getSession().getCurrentPickNo()).isEqualTo(6);
        assertThat(skipTo6.getSession().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(skipTo7.getSession().getCurrentPickNo()).isEqualTo(7);
        assertThat(skipTo7.getSession().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(skipTo8.getSession().getCurrentPickNo()).isEqualTo(8);
        assertThat(skipTo8.getSession().getCurrentDraftTeamId()).isEqualTo(teamAId);
        assertThat(draftOrderRepository.findByDraftSessionIdAndPickNo(sessionId, 6L)).isPresent()
                .get()
                .extracting(DraftOrderEntity::getDraftTeamId)
                .isEqualTo(teamBId);
    }

    @Test
    void turn_beyond_existing_orders_repeats_existing_order_pattern() {
        AuthActor owner = createActor("owner-repeat-order", "Owner Repeat Order", "ROLE_USER");
        Long candidateId = createUser("candidate-repeat-order", "Candidate Repeat Order", "ROLE_USER");

        Long sessionId = createSession(owner, "Repeat Pattern Session", 3);
        Long teamAId = createTeam(owner, sessionId, "Pattern A", 1);
        Long teamBId = createTeam(owner, sessionId, "Pattern B", 2);
        Long teamCId = createTeam(owner, sessionId, "Pattern C", 3);
        createCandidate(owner, sessionId, candidateId, "Candidate Repeat Order", "ZERG");
        createOrder(owner, sessionId, 1L, teamAId);
        createOrder(owner, sessionId, 2L, teamBId);
        createOrder(owner, sessionId, 3L, teamCId);
        draftLiveCommandService.startSession(sessionId, owner);
        draftLiveCommandService.forceSkip(sessionId, owner, "skip 1");
        draftLiveCommandService.forceSkip(sessionId, owner, "skip 2");

        DraftLiveSnapshotResponseDto skipped = draftLiveCommandService.forceSkip(sessionId, owner, "skip 3");

        assertThat(skipped.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(skipped.getSession().getCurrentPickNo()).isEqualTo(4);
        assertThat(skipped.getSession().getCurrentDraftTeamId()).isEqualTo(teamAId);
        assertThat(draftOrderRepository.findByDraftSessionIdAndPickNo(sessionId, 4L)).isPresent()
                .get()
                .extracting(DraftOrderEntity::getDraftTeamId)
                .isEqualTo(teamAId);
    }

    @Test
    void picking_last_waiting_candidate_finishes_session() {
        AuthActor owner = createActor("owner-final-pick", "Owner Final Pick", "ROLE_USER");
        AuthActor pickerA = createActor("picker-final-pick", "Picker Final Pick", "ROLE_USER");
        Long candidateId = createUser("candidate-final-pick", "Candidate Final Pick", "ROLE_USER");

        Long sessionId = createSession(owner, "Final Pick Session");
        Long teamAId = createTeam(owner, sessionId, "Final Pick Team", 1);
        assignPicker(owner, teamAId, pickerA.userPk());
        createCandidate(owner, sessionId, candidateId, "Candidate Final Pick", "ZERG");
        createOrder(owner, sessionId, 1L, teamAId);
        draftLiveCommandService.startSession(sessionId, owner);

        DraftLiveSnapshotResponseDto result = draftLiveCommandService.pick(sessionId, candidateId, pickerA);

        assertThat(result.getSession().getStatus()).isEqualTo("FINISHED");
        assertThat(result.getSession().getCurrentDraftTeamId()).isNull();
        assertThat(result.getAvailableCandidates()).isEmpty();
        assertThat(result.getPickedCandidates()).hasSize(1);
    }

    @Test
    void force_skip_alone_does_not_finish_while_waiting_candidates_remain() {
        AuthActor owner = createActor("owner-force-skip-waiting", "Owner Force Skip Waiting", "ROLE_USER");
        Long candidateId = createUser("candidate-force-skip-waiting", "Candidate Force Skip Waiting", "ROLE_USER");

        Long sessionId = createSession(owner, "Force Skip Waiting Session");
        Long teamAId = createTeam(owner, sessionId, "Force Skip Team", 1);
        createCandidate(owner, sessionId, candidateId, "Candidate Force Skip Waiting", "ZERG");
        createOrder(owner, sessionId, 1L, teamAId);
        draftLiveCommandService.startSession(sessionId, owner);

        DraftLiveSnapshotResponseDto skipped = draftLiveCommandService.forceSkip(sessionId, owner, "only skip");

        assertThat(skipped.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(skipped.getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(skipped.getSession().getCurrentDraftTeamId()).isEqualTo(teamAId);
        assertThat(skipped.getAvailableCandidates()).hasSize(1);
    }

    @Test
    void resume_aligns_current_team_from_order_when_current_team_is_null() {
        AuthActor owner = createActor("owner06", "Owner Six", "ROLE_USER");
        Long sessionId = createSession(owner, "Resume Session");
        Long teamAId = createTeam(owner, sessionId, "Legacy A", 1);
        Long teamBId = createTeam(owner, sessionId, "Legacy B", 2);
        createOrder(owner, sessionId, 1L, teamAId);
        createOrder(owner, sessionId, 2L, teamBId);

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.update(
                session.getTitle(),
                "PAUSED",
                session.getOrderMode(),
                session.getTeamCount(),
                session.getPickTimeSeconds(),
                2,
                null,
                null,
                LocalDateTime.now().minusMinutes(1),
                null
        );

        DraftLiveSnapshotResponseDto resumed = draftLiveCommandService.resumeSession(sessionId, owner, 20);

        assertThat(resumed.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(resumed.getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(resumed.getSession().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(resumed.getCurrentTurn()).isNotNull();
        assertThat(resumed.getCurrentTurn().getTeamId()).isEqualTo(teamBId);
    }

    private Long createSession(AuthActor actor, String title) {
        return createSession(actor, title, 2);
    }

    private Long createSession(AuthActor actor, String title, int teamCount) {
        return createSession(actor, title, teamCount, "BASIC");
    }

    private Long createSession(AuthActor actor, String title, int teamCount, String orderMode) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle(title);
        requestDto.setStatus("READY");
        requestDto.setOrderMode(orderMode);
        requestDto.setTeamCount(teamCount);
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

    private AuthActor createActor(String userId, String name, String role) {
        Long userPk = createUser(userId, name, role);
        return new AuthActor(userPk, userId, role);
    }

    private Long createUser(String userId, String name, String role) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .status("ACTIVE")
                .userType(role)
                .build();
        return userRepository.save(user).getId();
    }
}
