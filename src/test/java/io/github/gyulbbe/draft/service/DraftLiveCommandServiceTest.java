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
    private UserRepository userRepository;

    @Test
    void start_pause_resume는_세션상태와_마감시간을_갱신한다() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A팀", 1);
        createTeam(sessionId, "B팀", 2);
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
    void 지정된_픽커만_현재턴에서_픽할_수_있고_성공하면_다음턴으로_이동한다() {
        Long pickerAId = createUser("pickerA", "픽커A");
        Long pickerBId = createUser("pickerB", "픽커B");
        Long candidate1Id = createUser("candidate1", "후보1");
        Long candidate2Id = createUser("candidate2", "후보2");

        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "레드", 1);
        Long teamBId = createTeam(sessionId, "블루", 2);
        assignPicker(teamAId, pickerAId);
        assignPicker(teamBId, pickerBId);
        createCandidate(sessionId, candidate1Id, "후보1", "ZERG");
        createCandidate(sessionId, candidate2Id, "후보2", "PROTOSS");
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
        assertThat(result.getPickedCandidates().get(0).getCandidateUserId()).isEqualTo(candidate1Id);
        assertThat(result.getRecentPicks()).hasSize(1);
        assertThat(result.getTeams().stream()
                .filter(team -> team.getId().equals(teamAId))
                .findFirst()
                .orElseThrow()
                .getRoster()).hasSize(1);
    }

    @Test
    void 지정된_픽커가_아니면_픽할_수_없다() {
        Long pickerId = createUser("pickerA2", "픽커A2");
        Long otherUserId = createUser("otherA2", "다른유저");
        Long candidate1Id = createUser("candidate3", "후보3");

        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "알파", 1);
        assignPicker(teamAId, pickerId);
        createCandidate(sessionId, candidate1Id, "후보3", "TERRAN");
        createOrder(sessionId, 1L, teamAId);
        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));

        assertThatThrownBy(() ->
                draftLiveCommandService.pick(sessionId, candidate1Id, new AuthActor(otherUserId, "otherA2", "ROLE_USER"))
        ).hasMessageContaining("픽 권한");
    }

    @Test
    void 마지막_픽이면_세션은_finished가_된다() {
        Long pickerId = createUser("pickerLast", "픽커Last");
        Long candidate1Id = createUser("candidateLast", "후보Last");

        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "마지막팀", 1);
        assignPicker(teamAId, pickerId);
        createCandidate(sessionId, candidate1Id, "후보Last", "RANDOM");
        createOrder(sessionId, 1L, teamAId);
        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));

        DraftLiveSnapshotResponseDto result = draftLiveCommandService.pick(
                sessionId,
                candidate1Id,
                new AuthActor(pickerId, "pickerLast", "ROLE_USER")
        );

        assertThat(result.getSession().getStatus()).isEqualTo("FINISHED");
        assertThat(result.getSession().getCurrentDraftTeamId()).isNull();
        assertThat(result.getSession().getDeadlineAt()).isNull();
    }

    @Test
    void extendTime은_마감시간을_연장한다() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "연장팀", 1);
        createOrder(sessionId, 1L, teamAId);

        AuthActor admin = new AuthActor(1L, "admin", "ROLE_ADMIN");
        DraftLiveSnapshotResponseDto started = draftLiveCommandService.startSession(sessionId, admin);
        DraftLiveSnapshotResponseDto extended = draftLiveCommandService.extendTime(sessionId, admin, 20);

        assertThat(extended.getSession().getDeadlineAt()).isAfter(started.getSession().getDeadlineAt());
        assertThat(extended.getCurrentTurn().getRemainingSeconds()).isGreaterThan(started.getCurrentTurn().getRemainingSeconds());
    }

    @Test
    void forceSkip은_pick없이_다음턴으로_넘긴다() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        Long teamBId = createTeam(sessionId, "B", 2);
        createOrder(sessionId, 1L, teamAId);
        createOrder(sessionId, 2L, teamBId);

        AuthActor admin = new AuthActor(1L, "admin", "ROLE_ADMIN");
        draftLiveCommandService.startSession(sessionId, admin);

        DraftLiveSnapshotResponseDto skipped = draftLiveCommandService.forceSkip(sessionId, admin, "manual");

        assertThat(skipped.getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(skipped.getSession().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(skipped.getRecentPicks()).isEmpty();
    }

    @Test
    void finishSession은_즉시_finished로_바꾼다() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        createOrder(sessionId, 1L, teamAId);

        AuthActor admin = new AuthActor(1L, "admin", "ROLE_ADMIN");
        draftLiveCommandService.startSession(sessionId, admin);

        DraftLiveSnapshotResponseDto finished = draftLiveCommandService.finishSession(sessionId, admin, "manual-finish");

        assertThat(finished.getSession().getStatus()).isEqualTo("FINISHED");
        assertThat(finished.getSession().getCurrentDraftTeamId()).isNull();
        assertThat(finished.getSession().getDeadlineAt()).isNull();
    }

    private Long createSession() {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle("라이브 명령 세션");
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
