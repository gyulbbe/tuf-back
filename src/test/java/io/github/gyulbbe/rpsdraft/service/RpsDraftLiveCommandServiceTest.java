package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSnapshotResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionCreateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionDetailResponseDto;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftPickEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftTeamEntity;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftCandidateRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftPickRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftQueryRepositoryImpl;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftSessionRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftTeamRepository;
import io.github.gyulbbe.rpsdraft.ws.RpsDraftEventPublisher;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        RpsDraftQueryRepositoryImpl.class,
        RpsDraftPermissionService.class,
        RpsDraftService.class,
        RpsDraftAdminService.class,
        RpsDraftSnapshotService.class,
        RpsDraftEventPublisher.class,
        RpsDraftLiveCommandService.class
})
@EntityScan(basePackageClasses = {
        RpsDraftSessionEntity.class,
        RpsDraftTeamEntity.class,
        RpsDraftCandidateEntity.class,
        RpsDraftPickEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        RpsDraftSessionRepository.class,
        RpsDraftTeamRepository.class,
        RpsDraftCandidateRepository.class,
        RpsDraftPickRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rpsdraftlivecommanddb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class RpsDraftLiveCommandServiceTest {

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private RpsDraftService rpsDraftService;

    @Autowired
    private RpsDraftLiveCommandService rpsDraftLiveCommandService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void startSession_moves_to_rps_pending() {
        Fixture fixture = createReadyFixture(2);

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftLiveCommandService.startSession(
                fixture.sessionId,
                actor(fixture.ownerId, "owner")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_RPS_PENDING);
        assertThat(snapshot.getSession().getCurrentPickNo()).isEqualTo(1);
        assertThat(snapshot.getSession().getCurrentDraftTeamId()).isNull();
        assertThat(snapshot.getSession().getPendingDraftTeamId()).isNull();
    }

    @Test
    void one_submission_keeps_result_hidden() {
        Fixture fixture = createStartedFixture(2);

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_ROCK,
                actor(fixture.team1PickerId, "picker1")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_RPS_PENDING);
        assertThat(snapshot.getRps().isTeam1Submitted()).isTrue();
        assertThat(snapshot.getRps().isTeam2Submitted()).isFalse();
        assertThat(snapshot.getRps().getTeam1Choice()).isNull();
        assertThat(snapshot.getRps().getResult()).isEqualTo(RpsDraftSessionEntity.RPS_RESULT_PENDING);
    }

    @Test
    void two_submissions_resolve_winner() {
        Fixture fixture = createStartedFixture(2);

        rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_ROCK,
                actor(fixture.team1PickerId, "picker1")
        );
        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_SCISSORS,
                actor(fixture.team2PickerId, "picker2")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_PICKING);
        assertThat(snapshot.getSession().getCurrentDraftTeamId()).isEqualTo(fixture.team1Id);
        assertThat(snapshot.getSession().getPendingDraftTeamId()).isEqualTo(fixture.team2Id);
        assertThat(snapshot.getRps().getResult()).isEqualTo(RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN);
    }

    @Test
    void draw_resets_back_to_rps_pending() {
        Fixture fixture = createStartedFixture(2);

        rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_ROCK,
                actor(fixture.team1PickerId, "picker1")
        );
        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_ROCK,
                actor(fixture.team2PickerId, "picker2")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_RPS_PENDING);
        assertThat(snapshot.getSession().getCurrentDraftTeamId()).isNull();
        assertThat(snapshot.getSession().getPendingDraftTeamId()).isNull();
        assertThat(snapshot.getRps().isTeam1Submitted()).isFalse();
        assertThat(snapshot.getRps().isTeam2Submitted()).isFalse();
        assertThat(snapshot.getRps().getResult()).isEqualTo(RpsDraftSessionEntity.RPS_RESULT_PENDING);
    }

    @Test
    void winner_pick_transfers_turn_to_pending_team() {
        Fixture fixture = createStartedFixture(3);
        resolveTeam1Win(fixture);

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftLiveCommandService.pick(
                fixture.sessionId,
                fixture.candidateIds[0],
                actor(fixture.team1PickerId, "picker1")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_PICKING);
        assertThat(snapshot.getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(snapshot.getSession().getCurrentDraftTeamId()).isEqualTo(fixture.team2Id);
        assertThat(snapshot.getSession().getPendingDraftTeamId()).isNull();
    }

    @Test
    void loser_pick_returns_to_rps_pending() {
        Fixture fixture = createStartedFixture(4);
        resolveTeam1Win(fixture);
        rpsDraftLiveCommandService.pick(
                fixture.sessionId,
                fixture.candidateIds[0],
                actor(fixture.team1PickerId, "picker1")
        );

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftLiveCommandService.pick(
                fixture.sessionId,
                fixture.candidateIds[1],
                actor(fixture.team2PickerId, "picker2")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_RPS_PENDING);
        assertThat(snapshot.getSession().getCurrentPickNo()).isEqualTo(3);
        assertThat(snapshot.getSession().getCurrentDraftTeamId()).isNull();
        assertThat(snapshot.getSession().getPendingDraftTeamId()).isNull();
        assertThat(snapshot.getRps().getResult()).isEqualTo(RpsDraftSessionEntity.RPS_RESULT_PENDING);
    }

    @Test
    void last_pick_finishes_session() {
        Fixture fixture = createStartedFixture(1);
        resolveTeam1Win(fixture);

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftLiveCommandService.pick(
                fixture.sessionId,
                fixture.candidateIds[0],
                actor(fixture.team1PickerId, "picker1")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_FINISHED);
        assertThat(snapshot.getSession().getEndedAt()).isNotNull();
    }

    @Test
    void unauthorized_user_cannot_submit_or_pick() {
        Fixture fixture = createStartedFixture(2);
        Long strangerId = createUser("stranger-live", "stranger", "ROLE_USER");

        assertThatThrownBy(() -> rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_ROCK,
                actor(strangerId, "stranger")
        )).hasMessageContaining("picker assigned");

        resolveTeam1Win(fixture);

        assertThatThrownBy(() -> rpsDraftLiveCommandService.pick(
                fixture.sessionId,
                fixture.candidateIds[0],
                actor(strangerId, "stranger")
        )).hasMessageContaining("picker assigned");
    }

    private void resolveTeam1Win(Fixture fixture) {
        rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_ROCK,
                actor(fixture.team1PickerId, "picker1")
        );
        rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_SCISSORS,
                actor(fixture.team2PickerId, "picker2")
        );
    }

    private Fixture createStartedFixture(int candidateCount) {
        Fixture fixture = createReadyFixture(candidateCount);
        rpsDraftLiveCommandService.startSession(fixture.sessionId, actor(fixture.ownerId, "owner"));
        return fixture;
    }

    private Fixture createReadyFixture(int candidateCount) {
        Long ownerId = createUser("owner-live-" + candidateCount, "owner", "ROLE_USER");
        Long team1PickerId = createUser("picker-live-a-" + candidateCount, "pickerA", "ROLE_USER");
        Long team2PickerId = createUser("picker-live-b-" + candidateCount, "pickerB", "ROLE_USER");

        Long[] candidateIds = new Long[candidateCount];
        List<Long> candidateUserIds = new ArrayList<>();
        for (int i = 0; i < candidateCount; i++) {
            candidateIds[i] = createUser("candidate-live-" + candidateCount + "-" + i, "candidate" + i, "ROLE_USER");
            candidateUserIds.add(candidateIds[i]);
        }

        RpsDraftSessionDetailResponseDto session = createSession(
                ownerId,
                "live command " + candidateCount,
                team1PickerId,
                team2PickerId,
                candidateUserIds
        );

        Long team1Id = session.getTeams().get(0).getId();
        Long team2Id = session.getTeams().get(1).getId();

        return new Fixture(session.getId(), ownerId, team1Id, team2Id, team1PickerId, team2PickerId, candidateIds);
    }

    private RpsDraftSessionDetailResponseDto createSession(
            Long ownerId,
            String title,
            Long team1PickerId,
            Long team2PickerId,
            List<Long> candidateUserIds
    ) {
        RpsDraftSessionCreateRequestDto requestDto = new RpsDraftSessionCreateRequestDto();
        requestDto.setTitle(title);
        requestDto.setTeam1PickerUserId(team1PickerId);
        requestDto.setTeam2PickerUserId(team2PickerId);
        requestDto.setCandidateUserIds(candidateUserIds);
        return rpsDraftService.createSession(requestDto, actor(ownerId, "owner")).getData();
    }

    private RpsDraftActor actor(Long userPk, String userId) {
        return new RpsDraftActor(userPk, userId, "ROLE_USER");
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

    private record Fixture(
            Long sessionId,
            Long ownerId,
            Long team1Id,
            Long team2Id,
            Long team1PickerId,
            Long team2PickerId,
            Long[] candidateIds
    ) {
    }
}
