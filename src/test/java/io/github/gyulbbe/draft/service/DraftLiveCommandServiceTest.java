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
    private UserRepository userRepository;

    @Test
    void start_pause_resume_updates_session_state() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        createTeam(sessionId, "B", 2);
        createOrder(sessionId, 1L, teamAId);

        AuthActor admin = new AuthActor(1L, "admin", "ROLE_ADMIN");

        DraftLiveSnapshotResponseDto started = draftLiveCommandService.startSession(sessionId, admin);
        DraftLiveSnapshotResponseDto paused = draftLiveCommandService.pauseSession(sessionId, admin);
        DraftLiveSnapshotResponseDto resumed = draftLiveCommandService.resumeSession(sessionId, admin, 15);

        assertThat(started.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(started.getSession().getCurrentPickNo()).isEqualTo(1);
        assertThat(started.getSession().getCurrentDraftTeamId()).isEqualTo(teamAId);
        assertThat(started.getSession().getDeadlineAt()).isNotNull();

        assertThat(paused.getSession().getStatus()).isEqualTo("PAUSED");
        assertThat(paused.getSession().getDeadlineAt()).isNull();

        assertThat(resumed.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(resumed.getSession().getDeadlineAt()).isNotNull();
    }

    @Test
    void start_rejects_paused_session() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "pausedA", 1);
        createOrder(sessionId, 1L, teamAId);
        AuthActor admin = new AuthActor(1L, "admin", "ROLE_ADMIN");

        draftLiveCommandService.startSession(sessionId, admin);
        draftLiveCommandService.pauseSession(sessionId, admin);

        assertThatThrownBy(() -> draftLiveCommandService.startSession(sessionId, admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pick_advances_to_next_team() {
        Long pickerAId = createUser("pickerA", "pickerA");
        Long pickerBId = createUser("pickerB", "pickerB");
        Long candidate1Id = createUser("candidate1", "candidate1");
        Long candidate2Id = createUser("candidate2", "candidate2");

        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "red", 1);
        Long teamBId = createTeam(sessionId, "blue", 2);
        assignPicker(teamAId, pickerAId);
        assignPicker(teamBId, pickerBId);
        createCandidate(sessionId, candidate1Id, "candidate1", "ZERG");
        createCandidate(sessionId, candidate2Id, "candidate2", "PROTOSS");
        createOrder(sessionId, 1L, teamAId);
        createOrder(sessionId, 2L, teamBId);
        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));

        DraftLiveSnapshotResponseDto result = draftLiveCommandService.pick(
                sessionId,
                candidate1Id,
                new AuthActor(pickerAId, "pickerA", "ROLE_USER")
        );

        assertThat(result.getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(result.getSession().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(result.getPickedCandidates()).hasSize(1);
        assertThat(result.getRecentPicks()).hasSize(1);
    }

    @Test
    void pick_rejects_non_picker() {
        Long pickerId = createUser("pickerA2", "pickerA2");
        Long otherUserId = createUser("otherA2", "otherA2");
        Long candidateId = createUser("candidate3", "candidate3");

        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "alpha", 1);
        assignPicker(teamAId, pickerId);
        createCandidate(sessionId, candidateId, "candidate3", "TERRAN");
        createOrder(sessionId, 1L, teamAId);
        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));

        assertThatThrownBy(() ->
                draftLiveCommandService.pick(sessionId, candidateId, new AuthActor(otherUserId, "otherA2", "ROLE_USER"))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_requires_first_order_to_start() {
        Long sessionId = createSession();

        assertThatThrownBy(() -> draftLiveCommandService.startSession(
                sessionId,
                new AuthActor(1L, "admin", "ROLE_ADMIN")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void last_pick_finishes_session_when_no_next_order() {
        Long pickerId = createUser("lastPicker", "lastPicker");
        Long candidateId = createUser("lastCandidate", "lastCandidate");

        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "lastTeam", 1);
        assignPicker(teamId, pickerId);
        createCandidate(sessionId, candidateId, "lastCandidate", "RANDOM");
        createOrder(sessionId, 1L, teamId);

        AuthActor admin = new AuthActor(1L, "admin", "ROLE_ADMIN");
        draftLiveCommandService.startSession(sessionId, admin);

        DraftLiveSnapshotResponseDto picked = draftLiveCommandService.pick(
                sessionId,
                candidateId,
                new AuthActor(pickerId, "lastPicker", "ROLE_USER")
        );

        assertThat(picked.getSession().getStatus()).isEqualTo("FINISHED");
        assertThat(picked.getSession().getCurrentDraftTeamId()).isNull();
        assertThat(picked.getSession().getDeadlineAt()).isNull();
    }

    @Test
    void resume_aligns_current_team_from_order_when_current_team_is_null() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "legacyA", 1);
        Long teamBId = createTeam(sessionId, "legacyB", 2);
        createOrder(sessionId, 1L, teamAId);
        createOrder(sessionId, 2L, teamBId);

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.update(
                session.getTitle(),
                "PAUSED",
                session.getTeamCount(),
                session.getPickTimeSeconds(),
                2,
                null,
                null,
                LocalDateTime.now().minusMinutes(1),
                null
        );

        DraftLiveSnapshotResponseDto resumed = draftLiveCommandService.resumeSession(
                sessionId,
                new AuthActor(1L, "admin", "ROLE_ADMIN"),
                20
        );

        assertThat(resumed.getSession().getStatus()).isEqualTo("LIVE");
        assertThat(resumed.getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(resumed.getSession().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(resumed.getCurrentTurn()).isNotNull();
        assertThat(resumed.getCurrentTurn().getTeamId()).isEqualTo(teamBId);
        assertThat(resumed.getSession().getDeadlineAt()).isNotNull();
    }

    private Long createSession() {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle("live command session");
        requestDto.setStatus("READY");
        requestDto.setTeamCount(2);
        requestDto.setPickTimeSeconds(30);
        requestDto.setCurrentPickNo(1);
        return draftService.createSession(requestDto).getData().getId();
    }

    private Long createTeam(Long sessionId, String teamName, int displayOrder) {
        DraftTeamRequestDto requestDto = new DraftTeamRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setTeamName(teamName);
        requestDto.setDisplayOrder(displayOrder);
        return draftService.createTeam(requestDto).getData().getId();
    }

    private void assignPicker(Long teamId, Long pickerUserId) {
        draftAdminService.assignPicker(teamId, pickerUserId, new AuthActor(1L, "admin", "ROLE_ADMIN"));
    }

    private void createCandidate(Long sessionId, Long candidateUserId, String candidateName, String race) {
        DraftCandidateRequestDto requestDto = new DraftCandidateRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setCandidateUserId(candidateUserId);
        requestDto.setCandidateName(candidateName);
        requestDto.setRace(race);
        requestDto.setStatus("WAITING");
        draftService.createCandidate(requestDto);
    }

    private void createOrder(Long sessionId, Long pickNo, Long teamId) {
        DraftOrderRequestDto requestDto = new DraftOrderRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setDraftTeamId(teamId);
        draftService.createOrder(requestDto);
    }

    private Long createUser(String userId, String name) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .status("ACTIVE")
                .userType("ROLE_USER")
                .build();
        return userRepository.save(user).getId();
    }
}
