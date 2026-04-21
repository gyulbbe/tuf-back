package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderRequestDto;
import io.github.gyulbbe.draft.dto.DraftPickRequestDto;
import io.github.gyulbbe.draft.dto.DraftPickResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionDetailResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionRequestDto;
import io.github.gyulbbe.draft.dto.DraftSessionSummaryResponseDto;
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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        DraftService.class,
        DraftLiveSessionTracker.class,
        DraftQueryRepositoryImpl.class,
        QueryDslConfig.class,
        DraftPermissionService.class,
        DraftAdminService.class
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
        "spring.datasource.url=jdbc:h2:mem:draftdb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DraftServiceTest {

    @Autowired
    private DraftService draftService;

    @Autowired
    private DraftAdminService draftAdminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private DraftTeamRepository draftTeamRepository;

    @Autowired
    private DraftCandidateRepository draftCandidateRepository;

    @Autowired
    private DraftOrderRepository draftOrderRepository;

    @Autowired
    private DraftPickRepository draftPickRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void getSession_returns_teams_candidates_and_orders_together() {
        Long pickerId = createUser("picker01", "picker01");
        Long candidateId = createUser("candidate01", "candidate01");

        Long sessionId = createSession("draft session", 2, 60, DraftSessionEntity.MODE_FIXED_ORDER);
        Long teamAId = createTeam(sessionId, "teamA", 1);
        createTeam(sessionId, "teamB", 2);
        assignPicker(teamAId, pickerId);
        createCandidate(sessionId, candidateId, "candidate01", "TERRAN");
        createOrder(sessionId, 1L, teamAId);

        entityManager.flush();
        entityManager.clear();

        ResponseDto<DraftSessionDetailResponseDto> response = draftService.getSession(sessionId);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getDraftMode()).isEqualTo(DraftSessionEntity.MODE_FIXED_ORDER);
        assertThat(response.getData().getTeams()).hasSize(2);
        assertThat(response.getData().getTeams().get(0).getPickerUserId()).isEqualTo(pickerId);
        assertThat(response.getData().getCandidates()).hasSize(1);
        assertThat(response.getData().getOrders()).hasSize(1);
    }

    @Test
    void createPick_updates_candidate_and_advances_session_in_fixed_order_mode() {
        Long pickerAId = createUser("pickerA", "pickerA");
        Long pickerBId = createUser("pickerB", "pickerB");
        Long candidate1Id = createUser("candidateA", "candidateA");
        Long candidate2Id = createUser("candidateB", "candidateB");

        Long sessionId = createSession("active draft", 2, 90, DraftSessionEntity.MODE_FIXED_ORDER);
        Long teamAId = createTeam(sessionId, "red", 1);
        Long teamBId = createTeam(sessionId, "blue", 2);
        assignPicker(teamAId, pickerAId);
        assignPicker(teamBId, pickerBId);
        createCandidate(sessionId, candidate1Id, "candidateA", "ZERG");
        createCandidate(sessionId, candidate2Id, "candidateB", "PROTOSS");
        createOrder(sessionId, 1L, teamAId);
        createOrder(sessionId, 2L, teamBId);
        updateSessionCurrentTurn(sessionId, teamAId);

        DraftPickRequestDto pickRequestDto = new DraftPickRequestDto();
        pickRequestDto.setDraftSessionId(sessionId);
        pickRequestDto.setPickNo(1L);
        pickRequestDto.setDraftTeamId(teamAId);
        pickRequestDto.setCandidateUserId(candidate1Id);
        pickRequestDto.setPickedByUserId(pickerAId);

        ResponseDto<DraftPickResponseDto> response = draftService.createPick(pickRequestDto);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getCandidateUserId()).isEqualTo(candidate1Id);

        ResponseDto<DraftCandidateResponseDto> candidateResponse = draftService.getCandidate(sessionId, candidate1Id);
        assertThat(candidateResponse.getData().getStatus()).isEqualTo("PICKED");
        assertThat(candidateResponse.getData().getPickedDraftTeamId()).isEqualTo(teamAId);

        ResponseDto<DraftSessionDetailResponseDto> sessionResponse = draftService.getSession(sessionId);
        assertThat(sessionResponse.getData().getCurrentPickNo()).isEqualTo(2);
        assertThat(sessionResponse.getData().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(sessionResponse.getData().getDeadlineAt()).isNotNull();
    }

    @Test
    void createPick_waits_for_next_assignment_in_manual_captain_mode() {
        Long pickerAId = createUser("manualPickerA", "manualPickerA");
        Long candidate1Id = createUser("manualCandidate1", "manualCandidate1");
        Long candidate2Id = createUser("manualCandidate2", "manualCandidate2");

        Long sessionId = createSession("manual draft", 2, 45, DraftSessionEntity.MODE_MANUAL_CAPTAIN);
        Long teamAId = createTeam(sessionId, "manualA", 1);
        createTeam(sessionId, "manualB", 2);
        assignPicker(teamAId, pickerAId);
        createCandidate(sessionId, candidate1Id, "manualCandidate1", "ZERG");
        createCandidate(sessionId, candidate2Id, "manualCandidate2", "PROTOSS");
        createOrder(sessionId, 1L, teamAId);
        updateSessionCurrentTurn(sessionId, teamAId);

        DraftPickRequestDto pickRequestDto = new DraftPickRequestDto();
        pickRequestDto.setDraftSessionId(sessionId);
        pickRequestDto.setPickNo(1L);
        pickRequestDto.setDraftTeamId(teamAId);
        pickRequestDto.setCandidateUserId(candidate1Id);
        pickRequestDto.setPickedByUserId(pickerAId);

        ResponseDto<DraftPickResponseDto> response = draftService.createPick(pickRequestDto);

        assertThat(response.getStatus()).isEqualTo(200);

        ResponseDto<DraftSessionDetailResponseDto> sessionResponse = draftService.getSession(sessionId);
        assertThat(sessionResponse.getData().getDraftMode()).isEqualTo(DraftSessionEntity.MODE_MANUAL_CAPTAIN);
        assertThat(sessionResponse.getData().getStatus()).isEqualTo("LIVE");
        assertThat(sessionResponse.getData().getCurrentPickNo()).isEqualTo(2);
        assertThat(sessionResponse.getData().getCurrentDraftTeamId()).isNull();
        assertThat(sessionResponse.getData().getDeadlineAt()).isNull();
    }

    @Test
    void deleteSession_removes_session_and_all_related_data() {
        Long pickerId = createUser("picker02", "picker02");
        Long candidateId = createUser("candidate02", "candidate02");

        Long sessionId = createSession("delete draft", 2, 45, DraftSessionEntity.MODE_FIXED_ORDER);
        Long teamId = createTeam(sessionId, "deleteTeam", 1);
        assignPicker(teamId, pickerId);
        createCandidate(sessionId, candidateId, "candidate02", "RANDOM");
        createOrder(sessionId, 1L, teamId);

        ResponseDto<Void> response = draftService.deleteSession(sessionId);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(draftSessionRepository.findById(sessionId)).isEmpty();
        assertThat(draftTeamRepository.findAllByDraftSessionId(sessionId)).isEmpty();
        assertThat(draftCandidateRepository.findAllByDraftSessionId(sessionId)).isEmpty();
        assertThat(draftOrderRepository.findAllByDraftSessionIdOrderByPickNoAsc(sessionId)).isEmpty();
        assertThat(draftPickRepository.findAllByDraftSessionIdOrderByPickNoAsc(sessionId)).isEmpty();
    }

    @Test
    void createPick_rejects_duplicate_candidate_in_same_session() {
        Long pickerAId = createUser("picker03", "picker03");
        Long pickerBId = createUser("picker04", "picker04");
        Long candidateId = createUser("candidate03", "candidate03");

        Long sessionId = createSession("duplicate guard", 2, 75, DraftSessionEntity.MODE_FIXED_ORDER);
        Long teamAId = createTeam(sessionId, "team1", 1);
        Long teamBId = createTeam(sessionId, "team2", 2);
        assignPicker(teamAId, pickerAId);
        assignPicker(teamBId, pickerBId);
        createCandidate(sessionId, candidateId, "candidate03", "TERRAN");
        createOrder(sessionId, 1L, teamAId);
        createOrder(sessionId, 2L, teamBId);
        updateSessionCurrentTurn(sessionId, teamAId);

        DraftPickRequestDto firstPick = new DraftPickRequestDto();
        firstPick.setDraftSessionId(sessionId);
        firstPick.setPickNo(1L);
        firstPick.setDraftTeamId(teamAId);
        firstPick.setCandidateUserId(candidateId);
        firstPick.setPickedByUserId(pickerAId);
        draftService.createPick(firstPick);

        DraftPickRequestDto secondPick = new DraftPickRequestDto();
        secondPick.setDraftSessionId(sessionId);
        secondPick.setPickNo(2L);
        secondPick.setDraftTeamId(teamBId);
        secondPick.setCandidateUserId(candidateId);
        secondPick.setPickedByUserId(pickerBId);

        ResponseDto<DraftPickResponseDto> response = draftService.createPick(secondPick);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("이미 픽된 후보");
        assertThat(draftPickRepository.findAllByDraftSessionIdOrderByPickNoAsc(sessionId)).hasSize(1);
    }

    @Test
    void deleteSession_removes_finished_session_and_all_children() {
        Long pickerAId = createUser("picker05", "picker05");
        Long pickerBId = createUser("picker06", "picker06");
        Long candidateId = createUser("candidate04", "candidate04");

        Long sessionId = createSession("delete finished session", 2, 30, DraftSessionEntity.MODE_FIXED_ORDER);
        Long teamAId = createTeam(sessionId, "teamA", 1);
        Long teamBId = createTeam(sessionId, "teamB", 2);
        assignPicker(teamAId, pickerAId);
        assignPicker(teamBId, pickerBId);
        createCandidate(sessionId, candidateId, "candidate4", "ZERG");
        createOrder(sessionId, 1L, teamAId);
        updateSessionCurrentTurn(sessionId, teamAId);

        DraftPickRequestDto pickRequestDto = new DraftPickRequestDto();
        pickRequestDto.setDraftSessionId(sessionId);
        pickRequestDto.setPickNo(1L);
        pickRequestDto.setDraftTeamId(teamAId);
        pickRequestDto.setCandidateUserId(candidateId);
        pickRequestDto.setPickedByUserId(pickerAId);

        ResponseDto<DraftPickResponseDto> pickResponse = draftService.createPick(pickRequestDto);

        assertThat(pickResponse.getStatus()).isEqualTo(200);
        assertThat(draftSessionRepository.findById(sessionId)).get()
                .extracting(DraftSessionEntity::getStatus)
                .isEqualTo("FINISHED");

        ResponseDto<Void> deleteResponse = draftService.deleteSession(sessionId);

        assertThat(deleteResponse.getStatus()).isEqualTo(200);
        assertThat(draftSessionRepository.findById(sessionId)).isEmpty();
        assertThat(draftTeamRepository.findAllByDraftSessionId(sessionId)).isEmpty();
        assertThat(draftCandidateRepository.findAllByDraftSessionId(sessionId)).isEmpty();
        assertThat(draftOrderRepository.findAllByDraftSessionIdOrderByPickNoAsc(sessionId)).isEmpty();
        assertThat(draftPickRepository.findAllByDraftSessionIdOrderByPickNoAsc(sessionId)).isEmpty();
        assertThat(draftService.getSession(sessionId).getStatus()).isEqualTo(500);
    }

    private Long createSession(String title, int teamCount, int pickTimeSeconds, String draftMode) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle(title);
        requestDto.setStatus("READY");
        requestDto.setTeamCount(teamCount);
        requestDto.setPickTimeSeconds(pickTimeSeconds);
        requestDto.setDraftMode(draftMode);
        requestDto.setCurrentPickNo(1);

        ResponseDto<DraftSessionSummaryResponseDto> response = draftService.createSession(requestDto);
        return response.getData().getId();
    }

    private void updateSessionCurrentTurn(Long sessionId, Long currentDraftTeamId) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setStatus("LIVE");
        requestDto.setCurrentPickNo(1);
        requestDto.setCurrentDraftTeamId(currentDraftTeamId);
        draftService.updateSession(sessionId, requestDto);
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

    private void createOrder(Long sessionId, Long pickNo, Long draftTeamId) {
        DraftOrderRequestDto requestDto = new DraftOrderRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setDraftTeamId(draftTeamId);
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
