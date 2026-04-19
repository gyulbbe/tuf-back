package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftLiveEventResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveEventType;
import io.github.gyulbbe.draft.dto.DraftOrderRequestDto;
import io.github.gyulbbe.draft.dto.DraftSessionRequestDto;
import io.github.gyulbbe.draft.dto.DraftTeamOperatorRequestDto;
import io.github.gyulbbe.draft.dto.DraftTeamRequestDto;
import io.github.gyulbbe.draft.entity.DraftCandidateEntity;
import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftPickEntity;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.entity.DraftTeamOperatorEntity;
import io.github.gyulbbe.draft.repository.DraftCandidateRepository;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import io.github.gyulbbe.draft.repository.DraftPickRepository;
import io.github.gyulbbe.draft.repository.DraftQueryRepositoryImpl;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamOperatorRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import io.github.gyulbbe.draft.ws.DraftEventPublisher;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

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
        DraftLiveCommandService.class
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
        "spring.datasource.url=jdbc:h2:mem:drafteventpublisherdb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DraftEventPublisherTest {

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void startSession후_afterCommit으로_session_started_이벤트를_발행한다() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A팀", 1);
        createOrder(sessionId, 1L, 1, teamAId);

        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));

        ArgumentCaptor<DraftLiveEventResponseDto> captor = ArgumentCaptor.forClass(DraftLiveEventResponseDto.class);
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/drafts/" + sessionId), captor.capture());

        DraftLiveEventResponseDto event = captor.getValue();
        assertThat(event.getType()).isEqualTo(DraftLiveEventType.SESSION_STARTED);
        assertThat(event.getSessionId()).isEqualTo(sessionId);
        assertThat(event.getSnapshot()).isNotNull();
        assertThat(event.getSnapshot().getPermissions()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pick후_afterCommit으로_pick_completed_이벤트를_발행한다() {
        Long captainAId = createUser("captain-event", "캡틴");
        Long candidate1Id = createUser("candidate-event", "후보1");
        Long candidate2Id = createUser("candidate-event-2", "후보2");

        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "레드", 1);
        Long teamBId = createTeam(sessionId, "블루", 2);
        createOperator(teamAId, captainAId, "CAPTAIN");
        createCandidate(sessionId, candidate1Id, "후보1", "ZERG");
        createCandidate(sessionId, candidate2Id, "후보2", "PROTOSS");
        createOrder(sessionId, 1L, 1, teamAId);
        createOrder(sessionId, 2L, 1, teamBId);
        draftAdminService.assignPicker(teamAId, captainAId, new AuthActor(1L, "admin", "ROLE_ADMIN"));

        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));
        clearInvocations(simpMessagingTemplate);

        draftLiveCommandService.pick(sessionId, candidate1Id, new AuthActor(captainAId, "captain-event", "ROLE_USER"));

        ArgumentCaptor<DraftLiveEventResponseDto> captor = ArgumentCaptor.forClass(DraftLiveEventResponseDto.class);
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/drafts/" + sessionId), captor.capture());

        DraftLiveEventResponseDto event = captor.getValue();
        assertThat(event.getType()).isEqualTo(DraftLiveEventType.PICK_COMPLETED);
        assertThat(event.getActorUserId()).isEqualTo(captainAId);
        assertThat(event.getSnapshot().getSession().getCurrentPickNo()).isEqualTo(2);
        assertThat(event.getSnapshot().getPermissions()).isNull();
    }

    private Long createSession() {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle("이벤트 세션");
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
