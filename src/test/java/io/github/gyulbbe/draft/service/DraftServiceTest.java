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
    void 세션_상세_조회는_팀_픽커_후보_순서를_함께_반환한다() {
        Long pickerId = createUser("picker01", "픽커");
        Long candidateId = createUser("candidate01", "후보1");

        Long sessionId = createSession("테스트 드래프트", 2, 60);
        Long teamAId = createTeam(sessionId, "A팀", 1);
        createTeam(sessionId, "B팀", 2);
        assignPicker(teamAId, pickerId);
        createCandidate(sessionId, candidateId, "후보1", "TERRAN");
        createOrder(sessionId, 1L, teamAId);

        entityManager.flush();
        entityManager.clear();

        ResponseDto<DraftSessionDetailResponseDto> response = draftService.getSession(sessionId);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getTeams()).hasSize(2);
        assertThat(response.getData().getTeams().get(0).getPickerUserId()).isEqualTo(pickerId);
        assertThat(response.getData().getCandidates()).hasSize(1);
        assertThat(response.getData().getOrders()).hasSize(1);
    }

    @Test
    void 픽을_생성하면_후보상태와_세션의_현재턴이_함께_갱신된다() {
        Long pickerAId = createUser("pickerA", "픽커A");
        Long pickerBId = createUser("pickerB", "픽커B");
        Long candidate1Id = createUser("candidateA", "후보A");
        Long candidate2Id = createUser("candidateB", "후보B");

        Long sessionId = createSession("진행중 드래프트", 2, 90);
        Long teamAId = createTeam(sessionId, "레드", 1);
        Long teamBId = createTeam(sessionId, "블루", 2);
        assignPicker(teamAId, pickerAId);
        assignPicker(teamBId, pickerBId);
        createCandidate(sessionId, candidate1Id, "후보A", "ZERG");
        createCandidate(sessionId, candidate2Id, "후보B", "PROTOSS");
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
    void 세션_삭제는_하위_데이터도_같이_정리한다() {
        Long pickerId = createUser("picker02", "픽커2");
        Long candidateId = createUser("candidate02", "후보2");

        Long sessionId = createSession("삭제용 드래프트", 2, 45);
        Long teamId = createTeam(sessionId, "삭제팀", 1);
        assignPicker(teamId, pickerId);
        createCandidate(sessionId, candidateId, "후보2", "RANDOM");
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
    void 같은_후보는_같은_세션에서_두번_픽할_수_없다() {
        Long pickerAId = createUser("picker03", "픽커3");
        Long pickerBId = createUser("picker04", "픽커4");
        Long candidateId = createUser("candidate03", "후보3");

        Long sessionId = createSession("중복 방지", 2, 75);
        Long teamAId = createTeam(sessionId, "1팀", 1);
        Long teamBId = createTeam(sessionId, "2팀", 2);
        assignPicker(teamAId, pickerAId);
        assignPicker(teamBId, pickerBId);
        createCandidate(sessionId, candidateId, "후보3", "TERRAN");
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

    private Long createSession(String title, int teamCount, int pickTimeSeconds) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle(title);
        requestDto.setStatus("READY");
        requestDto.setTeamCount(teamCount);
        requestDto.setPickTimeSeconds(pickTimeSeconds);
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
