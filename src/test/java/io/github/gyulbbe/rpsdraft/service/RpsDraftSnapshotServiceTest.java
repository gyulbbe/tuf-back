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
    private RpsDraftSnapshotService rpsDraftSnapshotService;

    @Autowired
    private RpsDraftLiveCommandService rpsDraftLiveCommandService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void snapshot_masks_choices_while_waiting_for_second_submission() {
        Fixture fixture = createStartedFixture(1);

        rpsDraftLiveCommandService.submitRps(
                fixture.sessionId,
                RpsDraftSessionEntity.RPS_ROCK,
                actor(fixture.team1PickerId, "picker1")
        );

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(
                fixture.sessionId,
                actor(fixture.team2PickerId, "picker2")
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_RPS_PENDING);
        assertThat(snapshot.getRps().isTeam1Submitted()).isTrue();
        assertThat(snapshot.getRps().isTeam2Submitted()).isFalse();
        assertThat(snapshot.getRps().getTeam1Choice()).isNull();
        assertThat(snapshot.getRps().getTeam2Choice()).isNull();
        assertThat(snapshot.getRps().getResult()).isEqualTo(RpsDraftSessionEntity.RPS_RESULT_PENDING);
    }

    @Test
    void snapshot_shows_current_and_pending_team_during_picking() {
        Fixture fixture = createStartedFixture(2);

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

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(fixture.sessionId, null);

        assertThat(snapshot.getSession().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_PICKING);
        assertThat(snapshot.getSession().getCurrentDraftTeamId()).isEqualTo(fixture.team1Id);
        assertThat(snapshot.getSession().getPendingDraftTeamId()).isEqualTo(fixture.team2Id);
        assertThat(snapshot.getRps().getTeam1Choice()).isEqualTo(RpsDraftSessionEntity.RPS_ROCK);
        assertThat(snapshot.getRps().getTeam2Choice()).isEqualTo(RpsDraftSessionEntity.RPS_SCISSORS);
        assertThat(snapshot.getRps().getResult()).isEqualTo(RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN);
    }

    @Test
    void snapshot_includes_roster_candidates_and_recent_picks() {
        Fixture fixture = createStartedFixture(3);

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
        rpsDraftLiveCommandService.pick(
                fixture.sessionId,
                fixture.candidateIds[0],
                actor(fixture.team1PickerId, "picker1")
        );

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(
                fixture.sessionId,
                actor(fixture.team2PickerId, "picker2")
        );

        assertThat(snapshot.getSession().getOwnerUserLoginId()).isEqualTo("owner-snap-3");
        assertThat(snapshot.getSession().getOwnerName()).isEqualTo("owner-snap-3");
        assertThat(snapshot.getSession().getOwnerUserLoginId()).isNotEqualTo("owner");
        assertThat(snapshot.getSession().getOwnerUserLoginId()).isNotEqualTo(String.valueOf(fixture.ownerId));

        var team = snapshot.getTeams().stream()
                .filter(candidateTeam -> candidateTeam.getId().equals(fixture.team1Id))
                .findFirst()
                .orElseThrow();
        assertThat(team.getPickerUserLoginId()).isEqualTo("picker-snap-a-3");
        assertThat(team.getPickerName()).isEqualTo("picker-snap-a-3");
        assertThat(team.getPickerUserLoginId()).isNotEqualTo("pickerA");
        assertThat(team.getPickerUserLoginId()).isNotEqualTo(String.valueOf(fixture.team1PickerId));

        var roster = team.getRoster();
        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).getPickNo()).isEqualTo(1L);
        assertThat(roster.get(0).getRoundNo()).isEqualTo(1L);
        assertThat(roster.get(0).getCandidateUserId()).isEqualTo(fixture.candidateIds[0]);
        assertThat(roster.get(0).getCandidateUserLoginId()).isEqualTo("candidate-snap-3-0");
        assertThat(roster.get(0).getCandidateName()).isEqualTo("candidate-snap-3-0");
        assertThat(roster.get(0).getCandidateUserLoginId()).isNotEqualTo("candidate0");
        assertThat(roster.get(0).getCandidateUserLoginId()).isNotEqualTo(String.valueOf(fixture.candidateIds[0]));
        assertThat(roster.get(0).getTier()).isEqualTo("T0");
        assertThat(roster.get(0).getRace()).isEqualTo("ZERG");
        assertThat(roster.get(0).getPickedByUserLoginId()).isEqualTo("picker-snap-a-3");
        assertThat(roster.get(0).getPickedByUserName()).isEqualTo("picker-snap-a-3");
        assertThat(roster.get(0).getPickedByUserLoginId()).isNotEqualTo("pickerA");
        assertThat(snapshot.getAvailableCandidates()).hasSize(2);
        assertThat(snapshot.getAvailableCandidates().get(0).getCandidateUserLoginId()).isEqualTo("candidate-snap-3-1");
        assertThat(snapshot.getAvailableCandidates().get(0).getCandidateName()).isEqualTo("candidate-snap-3-1");
        assertThat(snapshot.getAvailableCandidates().get(0).getCandidateUserLoginId()).isNotEqualTo("candidate1");
        assertThat(snapshot.getPickedCandidates()).hasSize(1);
        assertThat(snapshot.getPickedCandidates().get(0).getCandidateUserLoginId()).isEqualTo("candidate-snap-3-0");
        assertThat(snapshot.getPickedCandidates().get(0).getCandidateName()).isEqualTo("candidate-snap-3-0");
        assertThat(snapshot.getPickedCandidates().get(0).getCandidateUserLoginId()).isNotEqualTo("candidate0");
        assertThat(snapshot.getPickedCandidates().get(0).getTier()).isEqualTo("T0");
        assertThat(snapshot.getPickedCandidates().get(0).getRace()).isEqualTo("ZERG");
        assertThat(snapshot.getRecentPicks()).hasSize(1);
        assertThat(snapshot.getRecentPicks().get(0).getCandidateUserLoginId()).isEqualTo("candidate-snap-3-0");
        assertThat(snapshot.getRecentPicks().get(0).getCandidateName()).isEqualTo("candidate-snap-3-0");
        assertThat(snapshot.getRecentPicks().get(0).getCandidateUserLoginId()).isNotEqualTo("candidate0");
        assertThat(snapshot.getRecentPicks().get(0).getTier()).isEqualTo("T0");
        assertThat(snapshot.getRecentPicks().get(0).getRace()).isEqualTo("ZERG");
        assertThat(snapshot.getRecentPicks().get(0).getPickedByUserLoginId()).isEqualTo("picker-snap-a-3");
        assertThat(snapshot.getRecentPicks().get(0).getPickedByUserName()).isEqualTo("picker-snap-a-3");
        assertThat(snapshot.getRecentPicks().get(0).getPickedByUserLoginId()).isNotEqualTo("pickerA");
        assertThat(snapshot.getPermissions().getMyTeamId()).isEqualTo(fixture.team2Id);
        assertThat(snapshot.getPermissions().isCanPick()).isTrue();

        RpsDraftLiveSnapshotResponseDto broadcastSnapshot = rpsDraftSnapshotService.getBroadcastSnapshot(fixture.sessionId);
        assertThat(broadcastSnapshot.getPermissions()).isNull();
        assertThat(broadcastSnapshot.getSession().getOwnerUserLoginId()).isEqualTo("owner-snap-3");
        var broadcastTeam = broadcastSnapshot.getTeams().stream()
                .filter(candidateTeam -> candidateTeam.getId().equals(fixture.team1Id))
                .findFirst()
                .orElseThrow();
        assertThat(broadcastTeam.getPickerUserLoginId()).isEqualTo("picker-snap-a-3");
        assertThat(broadcastTeam.getRoster())
                .extracting("candidateUserLoginId")
                .containsExactly("candidate-snap-3-0");
        assertThat(broadcastSnapshot.getRecentPicks())
                .extracting("candidateUserLoginId", "pickedByUserLoginId")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("candidate-snap-3-0", "picker-snap-a-3"));
    }

    private Fixture createStartedFixture(int candidateCount) {
        Long ownerId = createUser("owner-snap-" + candidateCount, "owner", "ROLE_USER");
        Long team1PickerId = createUser("picker-snap-a-" + candidateCount, "pickerA", "ROLE_USER");
        Long team2PickerId = createUser("picker-snap-b-" + candidateCount, "pickerB", "ROLE_USER");

        Long[] candidateIds = new Long[candidateCount];
        List<Long> candidateUserIds = new ArrayList<>();
        for (int i = 0; i < candidateCount; i++) {
            String race = i % 2 == 0 ? "ZERG" : "TERRAN";
            candidateIds[i] = createUser(
                    "candidate-snap-" + candidateCount + "-" + i,
                    "candidate" + i,
                    "ROLE_USER",
                    "T" + i,
                    race
            );
            candidateUserIds.add(candidateIds[i]);
        }

        RpsDraftSessionDetailResponseDto session = createSession(
                ownerId,
                "snapshot session " + candidateCount,
                team1PickerId,
                team2PickerId,
                candidateUserIds
        );

        rpsDraftLiveCommandService.startSession(session.getId(), actor(ownerId, "owner"));

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
