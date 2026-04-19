package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.*;
import io.github.gyulbbe.draft.entity.*;
import io.github.gyulbbe.draft.repository.*;
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
        DraftTeamOperatorEntity.class,
        DraftCandidateEntity.class,
        DraftOrderEntity.class,
        DraftPickEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        DraftTeamOperatorRepository.class,
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
    private UserRepository userRepository;

    @Test
    void snapshot은_현재턴_팀로스터_후보상태_권한을_함께_반환한다() {
        Long captainAId = createUser("captainA", "캡틴A", "ROLE_USER");
        Long captainBId = createUser("captainB", "캡틴B", "ROLE_USER");
        Long candidate1Id = createUser("candidate1", "후보1", "ROLE_USER");
        Long candidate2Id = createUser("candidate2", "후보2", "ROLE_USER");

        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "레드", 1);
        Long teamBId = createTeam(sessionId, "블루", 2);
        createOperator(teamAId, captainAId, "CAPTAIN");
        createOperator(teamBId, captainBId, "CAPTAIN");
        assignPicker(teamAId, captainAId);

        createCandidate(sessionId, candidate1Id, "후보1", "ZERG");
        createCandidate(sessionId, candidate2Id, "후보2", "TERRAN");
        createOrder(sessionId, 1L, 1, teamAId);
        createOrder(sessionId, 2L, 1, teamBId);
        updateSession(sessionId, "LIVE", 1, teamAId, LocalDateTime.now().plusSeconds(30));
        createPick(sessionId, 1L, 1, teamAId, candidate1Id, captainAId);

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(
                sessionId,
                new AuthActor(captainBId, "captainB", "ROLE_USER")
        );

        assertThat(snapshot.getSession().getId()).isEqualTo(sessionId);
        assertThat(snapshot.getCurrentTurn().getPickNo()).isEqualTo(2L);
        assertThat(snapshot.getCurrentTurn().getTeamId()).isEqualTo(teamBId);
        assertThat(snapshot.getTeams()).hasSize(2);
        assertThat(snapshot.getTeams().stream().filter(team -> team.getId().equals(teamAId)).findFirst().orElseThrow().getRoster()).hasSize(1);
        assertThat(snapshot.getAvailableCandidates()).hasSize(1);
        assertThat(snapshot.getPickedCandidates()).hasSize(1);
        assertThat(snapshot.getRecentPicks()).hasSize(1);
        assertThat(snapshot.getPermissions().getMyTeamId()).isEqualTo(teamBId);
        assertThat(snapshot.getPermissions().isCanPick()).isFalse();
    }

    @Test
    void snapshot은_현재턴의_지정된_픽권한자에게_canPick_true를_준다() {
        Long captainId = createUser("captain01", "캡틴", "ROLE_USER");
        Long candidateId = createUser("candidate01", "후보", "ROLE_USER");

        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "알파", 1);
        createOperator(teamId, captainId, "CAPTAIN");
        assignPicker(teamId, captainId);
        createCandidate(sessionId, candidateId, "후보", "PROTOSS");
        createOrder(sessionId, 1L, 1, teamId);
        updateSession(sessionId, "LIVE", 1, teamId, LocalDateTime.now().plusSeconds(25));

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(
                sessionId,
                new AuthActor(captainId, "captain01", "ROLE_USER")
        );

        assertThat(snapshot.getPermissions().isCanPick()).isTrue();
        assertThat(snapshot.getPermissions().getMyTeamId()).isEqualTo(teamId);
        assertThat(snapshot.getPermissions().getMyRole()).isEqualTo("CAPTAIN");
        assertThat(snapshot.getCurrentTurn().getRemainingSeconds()).isGreaterThanOrEqualTo(0L);
    }

    private Long createSession() {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle("라이브 스냅샷 세션");
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

    private void createOperator(Long teamId, Long operatorUserId, String role) {
        DraftTeamOperatorRequestDto requestDto = new DraftTeamOperatorRequestDto();
        requestDto.setDraftTeamId(teamId);
        requestDto.setOperatorUserId(operatorUserId);
        requestDto.setRole(role);
        requestDto.setIsActive("Y");
        draftService.createOperator(requestDto);
    }

    private void assignPicker(Long teamId, Long operatorUserId) {
        draftAdminService.assignPicker(teamId, operatorUserId, new AuthActor(1L, "admin", "ROLE_ADMIN"));
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

    private void createOrder(Long sessionId, Long pickNo, int roundNo, Long teamId) {
        DraftOrderRequestDto requestDto = new DraftOrderRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setRoundNo(roundNo);
        requestDto.setDraftTeamId(teamId);
        draftService.createOrder(requestDto);
    }

    private void updateSession(Long sessionId, String status, Integer currentPickNo, Long currentTeamId, LocalDateTime deadlineAt) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setStatus(status);
        requestDto.setCurrentPickNo(currentPickNo);
        requestDto.setCurrentDraftTeamId(currentTeamId);
        requestDto.setDeadlineAt(deadlineAt);
        draftService.updateSession(sessionId, requestDto);
    }

    private void createPick(Long sessionId, Long pickNo, int roundNo, Long teamId, Long candidateUserId, Long pickedByUserId) {
        DraftPickRequestDto requestDto = new DraftPickRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setRoundNo(roundNo);
        requestDto.setDraftTeamId(teamId);
        requestDto.setCandidateUserId(candidateUserId);
        requestDto.setPickedByUserId(pickedByUserId);
        draftService.createPick(requestDto);
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
