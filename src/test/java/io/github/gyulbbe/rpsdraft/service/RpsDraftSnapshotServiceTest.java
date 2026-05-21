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

@DataJpaTest
@Import({
        QueryDslConfig.class,
        RpsDraftQueryRepositoryImpl.class,
        RpsDraftPermissionService.class,
        RpsDraftService.class,
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
        "spring.datasource.url=jdbc:h2:mem:rpsdraftsnapshotdb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class RpsDraftSnapshotServiceTest {

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private RpsDraftService rpsDraftService;

    @Autowired
    private RpsDraftLiveCommandService rpsDraftLiveCommandService;

    @Autowired
    private RpsDraftSnapshotService rpsDraftSnapshotService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void snapshot_allows_picker_to_submit_immediately_after_creation() {
        Fixture fixture = createStartedFixture(1);

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(
                fixture.sessionId,
                actor(fixture.team1PickerId, "picker-snap-a-1")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_RPS_PENDING);
        assertThat(snapshot.getSession().getStartedAt()).isNotNull();
        assertThat(snapshot.getPermissions().isCanSubmitRps()).isTrue();
        assertThat(snapshot.getPermissions().isCanPick()).isFalse();
    }

    @Test
    void snapshot_masks_choices_while_waiting_for_second_submission() {
        Fixture fixture = createStartedFixture(1);

        rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_ROCK,
                actor(fixture.team1PickerId, "picker-snap-a-1")
        );

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(
                fixture.sessionId,
                actor(fixture.team2PickerId, "picker-snap-b-1")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_RPS_PENDING);
        assertThat(snapshot.getRps().isTeam1Submitted()).isTrue();
        assertThat(snapshot.getRps().isTeam2Submitted()).isFalse();
        assertThat(snapshot.getRps().getTeam1Choice()).isNull();
        assertThat(snapshot.getRps().getTeam2Choice()).isNull();
        assertThat(snapshot.getRps().getResult()).isEqualTo(RpsDraftSessionEntity.RPS_RESULT_PENDING);
        assertThat(snapshot.getPermissions().isCanSubmitRps()).isTrue();
    }

    @Test
    void snapshot_shows_current_and_pending_team_during_picking() {
        Fixture fixture = createStartedFixture(2);
        resolveTeam1Win(fixture, 2);

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(fixture.sessionId, null);

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_PICKING);
        assertThat(snapshot.getSession().getCurrentDraftTeamId()).isEqualTo(fixture.team1Id);
        assertThat(snapshot.getSession().getPendingDraftTeamId()).isEqualTo(fixture.team2Id);
        assertThat(snapshot.getRps().getTeam1Choice()).isEqualTo(RpsDraftSessionEntity.RPS_ROCK);
        assertThat(snapshot.getRps().getTeam2Choice()).isEqualTo(RpsDraftSessionEntity.RPS_SCISSORS);
        assertThat(snapshot.getRps().getResult()).isEqualTo(RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN);
        assertThat(snapshot.getPermissions().isCanSubmitRps()).isFalse();
        assertThat(snapshot.getPermissions().isCanPick()).isFalse();
    }

    @Test
    void snapshot_includes_name_candidates_roster_recent_picks_and_permissions() {
        Fixture fixture = createStartedFixture(3);
        resolveTeam1Win(fixture, 3);

        rpsDraftLiveCommandService.pick(
                fixture.sessionId,
                fixture.candidateIds[0],
                actor(fixture.team1PickerId, "picker-snap-a-3")
        );

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(
                fixture.sessionId,
                actor(fixture.team2PickerId, "picker-snap-b-3")
        );

        assertThat(snapshot.getSession().getOwnerUserLoginId()).isEqualTo("owner-snap-3");
        assertThat(snapshot.getTeams()).extracting("pickerUserLoginId")
                .containsExactly("picker-snap-a-3", "picker-snap-b-3");
        assertThat(snapshot.getTeams().get(0).getRoster()).hasSize(1);
        assertThat(snapshot.getTeams().get(0).getRoster().get(0).getCandidateId()).isEqualTo(fixture.candidateIds[0]);
        assertThat(snapshot.getTeams().get(0).getRoster().get(0).getCandidateName()).isEqualTo("candidate-snap-3-1");
        assertThat(snapshot.getTeams().get(0).getRoster().get(0).getPickedByUserLoginId()).isEqualTo("picker-snap-a-3");
        assertThat(snapshot.getAvailableCandidates()).extracting("candidateName")
                .containsExactly("candidate-snap-3-2", "candidate-snap-3-3");
        assertThat(snapshot.getPickedCandidates()).extracting("candidateName").containsExactly("candidate-snap-3-1");
        assertThat(snapshot.getRecentPicks()).extracting("candidateName").containsExactly("candidate-snap-3-1");
        assertThat(snapshot.getRecentPicks()).extracting("pickedByUserLoginId").containsExactly("picker-snap-a-3");
        assertThat(snapshot.getPermissions().getMyTeamId()).isEqualTo(fixture.team2Id);
        assertThat(snapshot.getPermissions().isCanPick()).isTrue();

        RpsDraftLiveSnapshotResponseDto broadcastSnapshot = rpsDraftSnapshotService.getBroadcastSnapshot(fixture.sessionId);
        assertThat(broadcastSnapshot.getPermissions()).isNull();
        assertThat(broadcastSnapshot.getTeams().get(0).getRoster()).extracting("candidateName")
                .containsExactly("candidate-snap-3-1");
        assertThat(broadcastSnapshot.getRecentPicks()).extracting("candidateName")
                .containsExactly("candidate-snap-3-1");
    }

    private void resolveTeam1Win(Fixture fixture, int suffix) {
        rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_ROCK,
                actor(fixture.team1PickerId, "picker-snap-a-" + suffix)
        );
        rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_SCISSORS,
                actor(fixture.team2PickerId, "picker-snap-b-" + suffix)
        );
    }

    private Fixture createStartedFixture(int candidateCount) {
        return createFixture(candidateCount);
    }

    private Fixture createFixture(int candidateCount) {
        Long ownerId = createUser("owner-snap-" + candidateCount);
        Long team1PickerId = createUser("picker-snap-a-" + candidateCount);
        Long team2PickerId = createUser("picker-snap-b-" + candidateCount);

        List<String> candidateNames = new ArrayList<>();
        for (int i = 0; i < candidateCount; i++) {
            candidateNames.add("candidate-snap-" + candidateCount + "-" + (i + 1));
        }

        RpsDraftSessionDetailResponseDto session = createSession(
                ownerId,
                "snapshot session " + candidateCount,
                team1PickerId,
                team2PickerId,
                candidateNames
        );

        Long[] candidateIds = session.getCandidates().stream()
                .map(candidate -> candidate.getId())
                .toArray(Long[]::new);

        return new Fixture(
                session.getId(),
                ownerId,
                session.getTeams().get(0).getId(),
                session.getTeams().get(1).getId(),
                team1PickerId,
                team2PickerId,
                candidateIds
        );
    }

    private RpsDraftSessionDetailResponseDto createSession(
            Long ownerId,
            String title,
            Long team1PickerId,
            Long team2PickerId,
            List<String> candidateNames
    ) {
        RpsDraftSessionCreateRequestDto requestDto = new RpsDraftSessionCreateRequestDto();
        requestDto.setTitle(title);
        requestDto.setTeam1PickerUserId(team1PickerId);
        requestDto.setTeam2PickerUserId(team2PickerId);
        requestDto.setCandidateNames(candidateNames);
        return rpsDraftService.createSession(requestDto, actor(ownerId, "owner")).getData();
    }

    private RpsDraftActor actor(Long userPk, String userId) {
        return new RpsDraftActor(userPk, userId, "ROLE_USER");
    }

    private Long createUser(String userId) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(userId)
                .status("ACTIVE")
                .userType("ROLE_USER")
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
