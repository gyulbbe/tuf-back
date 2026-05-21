package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionCreateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionDetailResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionSummaryResponseDto;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftPickEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftTeamEntity;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftCandidateRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftPickRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftQueryRepositoryImpl;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftSessionRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftTeamRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        RpsDraftQueryRepositoryImpl.class,
        RpsDraftPermissionService.class,
        RpsDraftService.class
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
        "spring.datasource.url=jdbc:h2:mem:rpsdraftservicedb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class RpsDraftServiceTest {

    @Autowired
    private RpsDraftService rpsDraftService;

    @Autowired
    private RpsDraftSessionRepository rpsDraftSessionRepository;

    @Autowired
    private RpsDraftTeamRepository rpsDraftTeamRepository;

    @Autowired
    private RpsDraftCandidateRepository rpsDraftCandidateRepository;

    @Autowired
    private RpsDraftPickRepository rpsDraftPickRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createSession_creates_session_teams_and_name_candidates() {
        Long ownerId = createUser("owner01", "ACTIVE", "ROLE_USER");
        Long team1PickerId = createUser("pickerA01", "ACTIVE", "ROLE_USER");
        Long team2PickerId = createUser("pickerB01", "ACTIVE", "ROLE_USER");

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("rps session", team1PickerId, team2PickerId, List.of(" alpha ", "bravo", "charlie")),
                actor(ownerId, "owner01", "ROLE_USER")
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_RPS_PENDING);
        assertThat(response.getData().getStartedAt()).isNotNull();
        assertThat(response.getData().getRegDate()).isNotNull();
        assertThat(response.getData().getOwnerUserId()).isEqualTo(ownerId);
        assertThat(response.getData().getOwnerUserLoginId()).isEqualTo("owner01");
        assertThat(response.getData().getTeams()).extracting("teamName").containsExactly("pickerA01", "pickerB01");
        assertThat(response.getData().getTeams()).extracting("pickerUserId").containsExactly(team1PickerId, team2PickerId);
        assertThat(response.getData().getCandidates()).extracting("candidateName").containsExactly("alpha", "bravo", "charlie");
        assertThat(response.getData().getCandidates()).extracting("displayOrder").containsExactly(1, 2, 3);

        Long sessionId = response.getData().getId();
        assertThat(rpsDraftSessionRepository.findById(sessionId)).isPresent()
                .get()
                .extracting(RpsDraftSessionEntity::getOwnerUserId)
                .isEqualTo(ownerId);
        assertThat(rpsDraftTeamRepository.findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(sessionId))
                .extracting(RpsDraftTeamEntity::getPickerUserId)
                .containsExactly(team1PickerId, team2PickerId);
        assertThat(rpsDraftCandidateRepository.findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(sessionId))
                .hasSize(3)
                .extracting(RpsDraftCandidateEntity::getCandidateName)
                .containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void createSession_rejects_duplicate_candidate_names_case_insensitively() {
        Long ownerId = createUser("owner02", "ACTIVE", "ROLE_USER");
        Long team1PickerId = createUser("picker02a", "ACTIVE", "ROLE_USER");
        Long team2PickerId = createUser("picker02b", "ACTIVE", "ROLE_USER");

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("duplicate candidates", team1PickerId, team2PickerId, List.of("Alpha", " alpha ")),
                actor(ownerId, "owner02", "ROLE_USER")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Duplicate candidate names");
        assertThat(rpsDraftSessionRepository.count()).isZero();
    }

    @Test
    void createSession_rejects_blank_candidate_names() {
        Long ownerId = createUser("owner03", "ACTIVE", "ROLE_USER");
        Long team1PickerId = createUser("picker03a", "ACTIVE", "ROLE_USER");
        Long team2PickerId = createUser("picker03b", "ACTIVE", "ROLE_USER");

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("blank candidates", team1PickerId, team2PickerId, List.of(" ", "")),
                actor(ownerId, "owner03", "ROLE_USER")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("At least one candidate name");
        assertThat(rpsDraftSessionRepository.count()).isZero();
    }

    @Test
    void createSession_rejects_inactive_picker() {
        Long ownerId = createUser("owner04", "ACTIVE", "ROLE_USER");
        Long inactivePickerId = createUser("picker04a", "INACTIVE", "ROLE_USER");
        Long team2PickerId = createUser("picker04b", "ACTIVE", "ROLE_USER");

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("inactive picker", inactivePickerId, team2PickerId, List.of("candidate")),
                actor(ownerId, "owner04", "ROLE_USER")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Only ACTIVE users can be assigned as pickers");
        assertThat(rpsDraftSessionRepository.count()).isZero();
    }

    @Test
    void deleteSession_removes_session_teams_candidates_and_picks_for_owner() {
        Long ownerId = createUser("owner05", "ACTIVE", "ROLE_USER");
        Long team1PickerId = createUser("picker05a", "ACTIVE", "ROLE_USER");
        Long team2PickerId = createUser("picker05b", "ACTIVE", "ROLE_USER");
        Long sessionId = createLiveLikeSession(ownerId, team1PickerId, team2PickerId);

        ResponseDto<Void> response = rpsDraftService.deleteSession(
                sessionId,
                actor(ownerId, "owner05", "ROLE_USER")
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(rpsDraftSessionRepository.findById(sessionId)).isEmpty();
        assertThat(rpsDraftTeamRepository.findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(sessionId)).isEmpty();
        assertThat(rpsDraftCandidateRepository.findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(sessionId)).isEmpty();
        assertThat(rpsDraftPickRepository.findAllByRpsDraftSessionIdOrderByPickNoAsc(sessionId)).isEmpty();
    }

    @Test
    void deleteSession_allows_admin_even_when_not_owner() {
        Long ownerId = createUser("owner06", "ACTIVE", "ROLE_USER");
        Long adminId = createUser("admin06", "ACTIVE", "ROLE_ADMIN");
        Long team1PickerId = createUser("picker06a", "ACTIVE", "ROLE_USER");
        Long team2PickerId = createUser("picker06b", "ACTIVE", "ROLE_USER");
        Long sessionId = createLiveLikeSession(ownerId, team1PickerId, team2PickerId);

        ResponseDto<Void> response = rpsDraftService.deleteSession(
                sessionId,
                actor(adminId, "admin06", "ROLE_ADMIN")
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(rpsDraftSessionRepository.findById(sessionId)).isEmpty();
    }

    @Test
    void deleteSession_returns_forbidden_for_non_owner_non_admin() {
        Long ownerId = createUser("owner07", "ACTIVE", "ROLE_USER");
        Long otherId = createUser("other07", "ACTIVE", "ROLE_USER");
        Long team1PickerId = createUser("picker07a", "ACTIVE", "ROLE_USER");
        Long team2PickerId = createUser("picker07b", "ACTIVE", "ROLE_USER");
        Long sessionId = createLiveLikeSession(ownerId, team1PickerId, team2PickerId);

        ResponseDto<Void> response = rpsDraftService.deleteSession(
                sessionId,
                actor(otherId, "other07", "ROLE_USER")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getMessage()).contains("session owner or an admin");
        assertThat(rpsDraftSessionRepository.findById(sessionId)).isPresent();
    }

    @Test
    void listSessions_returns_all_sessions_with_active_first_then_newest_created() {
        Long ownerId = createUser("owner08", "ACTIVE", "ROLE_USER");
        Long pickerAId = createUser("picker08a", "ACTIVE", "ROLE_USER");
        Long pickerBId = createUser("picker08b", "ACTIVE", "ROLE_USER");

        RpsDraftSessionDetailResponseDto olderActive = rpsDraftService.createSession(
                createSessionRequest("older active", pickerAId, pickerBId, List.of("candidate-a")),
                actor(ownerId, "owner08", "ROLE_USER")
        ).getData();
        RpsDraftSessionDetailResponseDto finished = rpsDraftService.createSession(
                createSessionRequest("finished", pickerAId, pickerBId, List.of("candidate-b")),
                actor(ownerId, "owner08", "ROLE_USER")
        ).getData();
        RpsDraftSessionDetailResponseDto newerActive = rpsDraftService.createSession(
                createSessionRequest("newer active", pickerAId, pickerBId, List.of("candidate-c")),
                actor(ownerId, "owner08", "ROLE_USER")
        ).getData();

        RpsDraftSessionEntity finishedEntity = rpsDraftSessionRepository.findById(finished.getId()).orElseThrow();
        finishedEntity.finish(LocalDateTime.now());
        rpsDraftSessionRepository.flush();

        ResponseDto<List<RpsDraftSessionSummaryResponseDto>> response = rpsDraftService.listSessions();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData())
                .extracting(RpsDraftSessionSummaryResponseDto::getId)
                .containsExactly(newerActive.getId(), olderActive.getId(), finished.getId());
        assertThat(response.getData()).extracting(RpsDraftSessionSummaryResponseDto::getStatus)
                .containsExactly(
                        RpsDraftSessionEntity.STATUS_RPS_PENDING,
                        RpsDraftSessionEntity.STATUS_RPS_PENDING,
                        RpsDraftSessionEntity.STATUS_FINISHED
                );
        assertThat(response.getData()).extracting(RpsDraftSessionSummaryResponseDto::getRegDate)
                .doesNotContainNull();
    }

    private Long createLiveLikeSession(Long ownerId, Long team1PickerUserId, Long team2PickerUserId) {
        RpsDraftSessionDetailResponseDto session = rpsDraftService.createSession(
                createSessionRequest("live-like session", team1PickerUserId, team2PickerUserId, List.of("candidate-a", "candidate-b")),
                actor(ownerId, "owner", "ROLE_USER")
        ).getData();

        RpsDraftTeamEntity team1 = rpsDraftTeamRepository.findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(session.getId()).get(0);
        RpsDraftCandidateEntity candidate1 = rpsDraftCandidateRepository
                .findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(session.getId())
                .get(0);

        RpsDraftSessionEntity entity = rpsDraftSessionRepository.findById(session.getId()).orElseThrow();
        entity.resolveRpsRound(team1.getId(), session.getTeams().get(1).getId(), RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN);

        LocalDateTime now = LocalDateTime.now();
        candidate1.markPicked(team1.getId(), now);
        rpsDraftPickRepository.save(RpsDraftPickEntity.builder()
                .rpsDraftSessionId(session.getId())
                .pickNo(1L)
                .rpsDraftTeamId(team1.getId())
                .candidateId(candidate1.getId())
                .pickedByUserId(team1PickerUserId)
                .pickedAt(now)
                .build());
        return session.getId();
    }

    private RpsDraftSessionCreateRequestDto createSessionRequest(
            String title,
            Long team1PickerUserId,
            Long team2PickerUserId,
            List<String> candidateNames
    ) {
        RpsDraftSessionCreateRequestDto requestDto = new RpsDraftSessionCreateRequestDto();
        requestDto.setTitle(title);
        requestDto.setTeam1PickerUserId(team1PickerUserId);
        requestDto.setTeam2PickerUserId(team2PickerUserId);
        requestDto.setCandidateNames(candidateNames);
        return requestDto;
    }

    private RpsDraftActor actor(Long userPk, String userId, String role) {
        return new RpsDraftActor(userPk, userId, role);
    }

    private Long createUser(String userId, String status, String role) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(userId)
                .status(status)
                .userType(role)
                .build();
        return userRepository.save(user).getId();
    }
}
