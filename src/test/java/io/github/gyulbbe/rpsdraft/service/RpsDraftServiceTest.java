package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionCreateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionDetailResponseDto;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateId;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftPickEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftPickId;
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
    void listSessions_exposes_owner_user_login_id_from_user_id_not_name_or_pk() {
        Long ownerId = createUser("owner-list-login", "Owner List Real Name", "ACTIVE", null);
        Long team1PickerId = createUser("picker-list-a", "Picker List A Real Name", "ACTIVE", null);
        Long team2PickerId = createUser("picker-list-b", "Picker List B Real Name", "ACTIVE", null);
        createSession(ownerId, "list session", team1PickerId, team2PickerId, List.of());

        ResponseDto<List<io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionSummaryResponseDto>> response =
                rpsDraftService.listSessions();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).filteredOn("ownerUserId", ownerId)
                .extracting("ownerUserLoginId", "ownerName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("owner-list-login", "owner-list-login"));
        assertThat(response.getData()).filteredOn("ownerUserId", ownerId)
                .allSatisfy(session -> {
                    assertThat(session.getOwnerUserLoginId()).isNotEqualTo("Owner List Real Name");
                    assertThat(session.getOwnerUserLoginId()).isNotEqualTo(String.valueOf(ownerId));
                });
    }

    @Test
    void createSession_creates_session_teams_and_candidates_in_one_request() {
        Long ownerId = createUser("owner01", "Owner Real Name", "ACTIVE", null);
        Long team1PickerId = createUser("pickerA01", "Picker A Real Name", "ACTIVE", null);
        Long team2PickerId = createUser("pickerB01", "Picker B Real Name", "ACTIVE", null);
        Long candidate1Id = createUser("candidate01", "Candidate One", "ACTIVE", "ZERG");
        Long candidate2Id = createUser("candidate02", null, "ACTIVE", "TERRAN");

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("rps session", team1PickerId, team2PickerId, List.of(candidate1Id, candidate2Id)),
                actor(ownerId, "owner01")
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getStatus()).isEqualTo(RpsDraftSessionEntity.STATUS_READY);
        assertThat(response.getData().getOwnerUserId()).isEqualTo(ownerId);
        assertThat(response.getData().getOwnerUserLoginId()).isEqualTo("owner01");
        assertThat(response.getData().getOwnerName()).isEqualTo("owner01");
        assertThat(response.getData().getOwnerUserLoginId()).isNotEqualTo("Owner Real Name");
        assertThat(response.getData().getOwnerUserLoginId()).isNotEqualTo(String.valueOf(ownerId));
        assertThat(response.getData().getTeams()).hasSize(2);
        assertThat(response.getData().getTeams()).extracting("displayOrder").containsExactly(1, 2);
        assertThat(response.getData().getTeams()).extracting("teamName").containsExactly("pickerA01", "pickerB01");
        assertThat(response.getData().getTeams()).extracting("pickerUserId").containsExactly(team1PickerId, team2PickerId);
        assertThat(response.getData().getTeams()).extracting("pickerUserLoginId").containsExactly("pickerA01", "pickerB01");
        assertThat(response.getData().getTeams()).extracting("pickerName").containsExactly("pickerA01", "pickerB01");
        assertThat(response.getData().getTeams()).allSatisfy(team -> {
            assertThat(team.getPickerUserLoginId()).isNotEqualTo("Picker A Real Name");
            assertThat(team.getPickerUserLoginId()).isNotEqualTo("Picker B Real Name");
            assertThat(team.getPickerUserLoginId()).isNotEqualTo(String.valueOf(team.getPickerUserId()));
        });
        assertThat(response.getData().getCandidates()).hasSize(2);
        assertThat(response.getData().getCandidates().get(0).getCandidateUserId()).isEqualTo(candidate1Id);
        assertThat(response.getData().getCandidates().get(0).getCandidateUserLoginId()).isEqualTo("candidate01");
        assertThat(response.getData().getCandidates().get(0).getCandidateName()).isEqualTo("candidate01");
        assertThat(response.getData().getCandidates().get(1).getCandidateUserId()).isEqualTo(candidate2Id);
        assertThat(response.getData().getCandidates().get(1).getCandidateUserLoginId()).isEqualTo("candidate02");
        assertThat(response.getData().getCandidates().get(1).getCandidateName()).isEqualTo("candidate02");
        assertThat(response.getData().getCandidates()).allSatisfy(candidate -> {
            assertThat(candidate.getCandidateUserLoginId()).isNotBlank();
            assertThat(candidate.getCandidateUserLoginId()).isNotEqualTo("Candidate One");
            assertThat(candidate.getCandidateUserLoginId()).isNotEqualTo(String.valueOf(candidate.getCandidateUserId()));
        });

        Long sessionId = response.getData().getId();
        assertThat(rpsDraftSessionRepository.findById(sessionId)).isPresent()
                .get()
                .extracting(RpsDraftSessionEntity::getOwnerUserId)
                .isEqualTo(ownerId);
        assertThat(rpsDraftTeamRepository.findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(sessionId))
                .extracting(RpsDraftTeamEntity::getPickerUserId)
                .containsExactly(team1PickerId, team2PickerId);
        assertThat(rpsDraftCandidateRepository.findAllByRpsDraftSessionId(sessionId))
                .hasSize(2)
                .extracting(RpsDraftCandidateEntity::getCandidateUserId)
                .containsExactlyInAnyOrder(candidate1Id, candidate2Id);
        assertThat(rpsDraftCandidateRepository.findById(new RpsDraftCandidateId(sessionId, candidate1Id))).isPresent()
                .get()
                .extracting(RpsDraftCandidateEntity::getCandidateName, RpsDraftCandidateEntity::getRace)
                .containsExactly("candidate01", "ZERG");
        assertThat(rpsDraftCandidateRepository.findById(new RpsDraftCandidateId(sessionId, candidate2Id))).isPresent()
                .get()
                .extracting(RpsDraftCandidateEntity::getCandidateName, RpsDraftCandidateEntity::getRace)
                .containsExactly("candidate02", "TERRAN");
    }

    @Test
    void getSession_allows_picker_login_id_null_only_for_unassigned_picker() {
        Long ownerId = createUser("owner-unassigned", "Owner Unassigned Real Name", "ACTIVE", null);
        Long pickerId = createUser("picker-assigned", "Picker Assigned Real Name", "ACTIVE", null);

        RpsDraftSessionEntity session = rpsDraftSessionRepository.save(RpsDraftSessionEntity.builder()
                .title("unassigned picker")
                .ownerUserId(ownerId)
                .build());
        rpsDraftTeamRepository.save(RpsDraftTeamEntity.builder()
                .rpsDraftSessionId(session.getId())
                .teamName("assigned")
                .displayOrder(1)
                .pickerUserId(pickerId)
                .build());
        rpsDraftTeamRepository.save(RpsDraftTeamEntity.builder()
                .rpsDraftSessionId(session.getId())
                .teamName("unassigned")
                .displayOrder(2)
                .pickerUserId(null)
                .build());

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.getSession(session.getId());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getOwnerUserLoginId()).isEqualTo("owner-unassigned");
        assertThat(response.getData().getTeams()).filteredOn("displayOrder", 1)
                .extracting("pickerUserLoginId", "pickerName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("picker-assigned", "picker-assigned"));
        assertThat(response.getData().getTeams()).filteredOn("displayOrder", 2)
                .extracting("pickerUserLoginId", "pickerName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(null, null));
    }

    @Test
    void createSession_requires_authenticated_actor() {
        Long team1PickerId = createUser("picker-auth-a", "pickerA", "ACTIVE", null);
        Long team2PickerId = createUser("picker-auth-b", "pickerB", "ACTIVE", null);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("auth required", team1PickerId, team2PickerId, null),
                null
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Authentication is required");
        assertThat(rpsDraftSessionRepository.count()).isZero();
    }

    @Test
    void createSession_rejects_same_picker_twice() {
        Long ownerId = createUser("owner02", "owner02", "ACTIVE", null);
        Long pickerId = createUser("picker02", "picker02", "ACTIVE", null);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("duplicate picker", pickerId, pickerId, null),
                actor(ownerId, "owner02")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Two distinct pickers");
        assertThat(rpsDraftSessionRepository.count()).isZero();
        assertThat(rpsDraftTeamRepository.count()).isZero();
        assertThat(rpsDraftCandidateRepository.count()).isZero();
    }

    @Test
    void createSession_rejects_duplicate_candidates() {
        Long ownerId = createUser("owner03", "owner03", "ACTIVE", null);
        Long team1PickerId = createUser("picker03a", "picker03a", "ACTIVE", null);
        Long team2PickerId = createUser("picker03b", "picker03b", "ACTIVE", null);
        Long candidateId = createUser("candidate03", "candidate03", "ACTIVE", null);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("duplicate candidates", team1PickerId, team2PickerId, List.of(candidateId, candidateId)),
                actor(ownerId, "owner03")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Duplicate candidate user ids");
        assertThat(rpsDraftSessionRepository.count()).isZero();
    }

    @Test
    void createSession_rejects_missing_picker() {
        Long ownerId = createUser("owner04", "owner04", "ACTIVE", null);
        Long team2PickerId = createUser("picker04b", "picker04b", "ACTIVE", null);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("missing picker", null, team2PickerId, null),
                actor(ownerId, "owner04")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Team 1 picker user id is required");
        assertThat(rpsDraftSessionRepository.count()).isZero();
    }

    @Test
    void createSession_rejects_unknown_picker() {
        Long ownerId = createUser("owner05", "owner05", "ACTIVE", null);
        Long team2PickerId = createUser("picker05b", "picker05b", "ACTIVE", null);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("unknown picker", 99999L, team2PickerId, null),
                actor(ownerId, "owner05")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Team 1 picker user could not be found");
        assertThat(rpsDraftSessionRepository.count()).isZero();
    }

    @Test
    void createSession_rejects_inactive_picker() {
        Long ownerId = createUser("owner06", "owner06", "ACTIVE", null);
        Long inactivePickerId = createUser("picker06a", "picker06a", "INACTIVE", null);
        Long team2PickerId = createUser("picker06b", "picker06b", "ACTIVE", null);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("inactive picker", inactivePickerId, team2PickerId, null),
                actor(ownerId, "owner06")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Only ACTIVE users can be assigned as pickers");
        assertThat(rpsDraftSessionRepository.count()).isZero();
    }

    @Test
    void createSession_rolls_back_when_candidate_is_missing() {
        Long ownerId = createUser("owner07", "owner07", "ACTIVE", null);
        Long team1PickerId = createUser("picker07a", "picker07a", "ACTIVE", null);
        Long team2PickerId = createUser("picker07b", "picker07b", "ACTIVE", null);
        Long candidateId = createUser("candidate07", "candidate07", "ACTIVE", null);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.createSession(
                createSessionRequest("missing candidate", team1PickerId, team2PickerId, List.of(candidateId, 123456L)),
                actor(ownerId, "owner07")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Candidate user could not be found");
        assertThat(rpsDraftSessionRepository.count()).isZero();
        assertThat(rpsDraftTeamRepository.count()).isZero();
        assertThat(rpsDraftCandidateRepository.count()).isZero();
    }

    @Test
    void registerCandidate_still_works_after_create_session() {
        Long ownerId = createUser("owner08", "owner08", "ACTIVE", null);
        Long team1PickerId = createUser("picker08a", "picker08a", "ACTIVE", null);
        Long team2PickerId = createUser("picker08b", "picker08b", "ACTIVE", null);
        Long candidateId = createUser("candidate08", "Candidate Eight Real Name", "ACTIVE", "PROTOSS");
        Long sessionId = createSession(ownerId, "register later", team1PickerId, team2PickerId, List.of());

        RpsDraftCandidateRequestDto requestDto = new RpsDraftCandidateRequestDto();
        requestDto.setCandidateUserId(candidateId);
        requestDto.setCandidateName("This Name Must Be Ignored");

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.registerCandidate(
                sessionId,
                requestDto,
                actor(ownerId, "owner08")
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getCandidates()).filteredOn("candidateUserId", candidateId)
                .extracting("candidateUserLoginId", "candidateName", "race")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("candidate08", "candidate08", "PROTOSS"));
        assertThat(response.getData().getCandidates()).filteredOn("candidateUserId", candidateId)
                .allSatisfy(candidate -> {
                    assertThat(candidate.getCandidateUserLoginId()).isNotNull();
                    assertThat(candidate.getCandidateUserLoginId()).isNotEqualTo("Candidate Eight Real Name");
                    assertThat(candidate.getCandidateUserLoginId()).isNotEqualTo(String.valueOf(candidateId));
                    assertThat(candidate.getCandidateName()).isEqualTo(candidate.getCandidateUserLoginId());
                });
        assertThat(rpsDraftCandidateRepository.findById(new RpsDraftCandidateId(sessionId, candidateId))).isPresent()
                .get()
                .extracting(RpsDraftCandidateEntity::getCandidateName, RpsDraftCandidateEntity::getRace)
                .containsExactly("candidate08", "PROTOSS");
    }

    @Test
    void registerCandidate_rejects_blank_user_id_string() {
        Long ownerId = createUser("owner-blank-user-id", "owner", "ACTIVE", null);
        Long team1PickerId = createUser("picker-blank-a", "pickerA", "ACTIVE", null);
        Long team2PickerId = createUser("picker-blank-b", "pickerB", "ACTIVE", null);
        Long candidateId = createUser("   ", "Candidate With Blank UserId", "ACTIVE", "ZERG");
        Long sessionId = createSession(ownerId, "blank candidate user id", team1PickerId, team2PickerId, List.of());

        RpsDraftCandidateRequestDto requestDto = new RpsDraftCandidateRequestDto();
        requestDto.setCandidateUserId(candidateId);
        requestDto.setCandidateName("Ignored Name");

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftService.registerCandidate(
                sessionId,
                requestDto,
                actor(ownerId, "owner-blank-user-id")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Candidate user's userId is required.");
        assertThat(rpsDraftCandidateRepository.findById(new RpsDraftCandidateId(sessionId, candidateId))).isEmpty();
    }

    @Test
    void deleteSession_removes_session_teams_candidates_and_picks_for_owner() {
        Long ownerId = createUser("owner09", "owner09", "ACTIVE", null);
        Long team1PickerId = createUser("picker09a", "picker09a", "ACTIVE", null);
        Long team2PickerId = createUser("picker09b", "picker09b", "ACTIVE", null);
        Long candidate1Id = createUser("candidate09a", "candidate09a", "ACTIVE", "ZERG");
        Long candidate2Id = createUser("candidate09b", "candidate09b", "ACTIVE", "TERRAN");
        Long sessionId = createLiveLikeSession(ownerId, team1PickerId, team2PickerId, candidate1Id, candidate2Id);

        ResponseDto<Void> response = rpsDraftService.deleteSession(sessionId, actor(ownerId, "owner09"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(rpsDraftSessionRepository.findById(sessionId)).isEmpty();
        assertThat(rpsDraftTeamRepository.findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(sessionId)).isEmpty();
        assertThat(rpsDraftCandidateRepository.findAllByRpsDraftSessionId(sessionId)).isEmpty();
        assertThat(rpsDraftPickRepository.findAllByRpsDraftSessionIdOrderByPickNoAsc(sessionId)).isEmpty();
    }

    @Test
    void deleteSession_allows_admin_even_when_not_owner() {
        Long ownerId = createUser("owner10", "owner10", "ACTIVE", null);
        Long adminId = createUser("admin10", "admin10", "ACTIVE", null, "ROLE_ADMIN");
        Long team1PickerId = createUser("picker10a", "picker10a", "ACTIVE", null);
        Long team2PickerId = createUser("picker10b", "picker10b", "ACTIVE", null);
        Long candidate1Id = createUser("candidate10a", "candidate10a", "ACTIVE", "ZERG");
        Long candidate2Id = createUser("candidate10b", "candidate10b", "ACTIVE", "TERRAN");
        Long sessionId = createLiveLikeSession(ownerId, team1PickerId, team2PickerId, candidate1Id, candidate2Id);

        ResponseDto<Void> response = rpsDraftService.deleteSession(sessionId, new RpsDraftActor(adminId, "admin10", "ROLE_ADMIN"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(rpsDraftSessionRepository.findById(sessionId)).isEmpty();
    }

    @Test
    void deleteSession_returns_forbidden_for_non_owner_non_admin() {
        Long ownerId = createUser("owner11", "owner11", "ACTIVE", null);
        Long otherId = createUser("other11", "other11", "ACTIVE", null);
        Long team1PickerId = createUser("picker11a", "picker11a", "ACTIVE", null);
        Long team2PickerId = createUser("picker11b", "picker11b", "ACTIVE", null);
        Long candidate1Id = createUser("candidate11a", "candidate11a", "ACTIVE", "ZERG");
        Long candidate2Id = createUser("candidate11b", "candidate11b", "ACTIVE", "TERRAN");
        Long sessionId = createLiveLikeSession(ownerId, team1PickerId, team2PickerId, candidate1Id, candidate2Id);

        ResponseDto<Void> response = rpsDraftService.deleteSession(sessionId, actor(otherId, "other11"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getMessage()).contains("session owner or an admin");
        assertThat(rpsDraftSessionRepository.findById(sessionId)).isPresent();
    }

    @Test
    void deleteSession_returns_not_found_for_missing_session() {
        Long ownerId = createUser("owner12", "owner12", "ACTIVE", null);

        ResponseDto<Void> response = rpsDraftService.deleteSession(99999L, actor(ownerId, "owner12"));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getMessage()).contains("could not be found");
    }

    private Long createSession(
            Long ownerId,
            String title,
            Long team1PickerUserId,
            Long team2PickerUserId,
            List<Long> candidateUserIds
    ) {
        return rpsDraftService.createSession(
                        createSessionRequest(title, team1PickerUserId, team2PickerUserId, candidateUserIds),
                        actor(ownerId, "owner")
                )
                .getData()
                .getId();
    }

    private Long createLiveLikeSession(
            Long ownerId,
            Long team1PickerUserId,
            Long team2PickerUserId,
            Long candidate1Id,
            Long candidate2Id
    ) {
        RpsDraftSessionEntity session = rpsDraftSessionRepository.save(
                RpsDraftSessionEntity.builder()
                        .title("live-like session")
                        .ownerUserId(ownerId)
                        .status(RpsDraftSessionEntity.STATUS_PICKING)
                        .currentPickNo(2)
                        .team1RpsChoice(RpsDraftSessionEntity.RPS_ROCK)
                        .team2RpsChoice(RpsDraftSessionEntity.RPS_SCISSORS)
                        .rpsResult(RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN)
                        .build()
        );

        RpsDraftTeamEntity team1 = rpsDraftTeamRepository.save(
                RpsDraftTeamEntity.builder()
                        .rpsDraftSessionId(session.getId())
                        .teamName("team-1")
                        .displayOrder(1)
                        .pickerUserId(team1PickerUserId)
                        .build()
        );
        RpsDraftTeamEntity team2 = rpsDraftTeamRepository.save(
                RpsDraftTeamEntity.builder()
                        .rpsDraftSessionId(session.getId())
                        .teamName("team-2")
                        .displayOrder(2)
                        .pickerUserId(team2PickerUserId)
                        .build()
        );

        session.resolveRpsRound(team1.getId(), team2.getId(), RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN);

        rpsDraftCandidateRepository.save(RpsDraftCandidateEntity.builder()
                .rpsDraftSessionId(session.getId())
                .candidateUserId(candidate1Id)
                .candidateName("candidate-1")
                .race("ZERG")
                .pickedRpsDraftTeamId(team1.getId())
                .status(RpsDraftCandidateEntity.STATUS_PICKED)
                .build());
        rpsDraftCandidateRepository.save(RpsDraftCandidateEntity.builder()
                .rpsDraftSessionId(session.getId())
                .candidateUserId(candidate2Id)
                .candidateName("candidate-2")
                .race("TERRAN")
                .status(RpsDraftCandidateEntity.STATUS_WAITING)
                .build());
        rpsDraftPickRepository.save(RpsDraftPickEntity.builder()
                .rpsDraftSessionId(session.getId())
                .pickNo(1L)
                .rpsDraftTeamId(team1.getId())
                .candidateUserId(candidate1Id)
                .pickedByUserId(team1PickerUserId)
                .pickedAt(LocalDateTime.now())
                .build());

        assertThat(rpsDraftPickRepository.findById(new RpsDraftPickId(session.getId(), 1L))).isPresent();
        return session.getId();
    }

    private RpsDraftSessionCreateRequestDto createSessionRequest(
            String title,
            Long team1PickerUserId,
            Long team2PickerUserId,
            List<Long> candidateUserIds
    ) {
        RpsDraftSessionCreateRequestDto requestDto = new RpsDraftSessionCreateRequestDto();
        requestDto.setTitle(title);
        requestDto.setTeam1PickerUserId(team1PickerUserId);
        requestDto.setTeam2PickerUserId(team2PickerUserId);
        requestDto.setCandidateUserIds(candidateUserIds);
        return requestDto;
    }

    private RpsDraftActor actor(Long userPk, String userId) {
        return new RpsDraftActor(userPk, userId, "ROLE_USER");
    }

    private Long createUser(String userId, String name, String status, String race) {
        return createUser(userId, name, status, race, "ROLE_USER");
    }

    private Long createUser(String userId, String name, String status, String race, String role) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .status(status)
                .race(race)
                .userType(role)
                .build();
        return userRepository.save(user).getId();
    }
}
