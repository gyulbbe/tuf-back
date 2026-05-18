package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftLiveEventResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveEventType;
import io.github.gyulbbe.draft.dto.DraftLiveNormalizedPositionDto;
import io.github.gyulbbe.draft.dto.DraftLivePreviewEndReason;
import io.github.gyulbbe.draft.dto.DraftLivePreviewPayloadDto;
import io.github.gyulbbe.draft.dto.DraftLivePreviewPhase;
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
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamMemberEntity;
import io.github.gyulbbe.league.repository.LeagueRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamMemberRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        DraftQueryRepositoryImpl.class,
        DraftLiveSessionTracker.class,
        DraftOrderPatternService.class,
        ProleagueDraftRosterSyncService.class,
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
        LeagueEntity.class,
        LeagueParticipationEntity.class,
        ProleagueTeamEntity.class,
        ProleagueTeamMemberEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        DraftCandidateRepository.class,
        DraftOrderRepository.class,
        DraftPickRepository.class,
        LeagueRepository.class,
        ProleagueTeamRepository.class,
        ProleagueTeamMemberRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:draftpreviewrelaydb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DraftLivePreviewRelayServiceTest {

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @MockitoBean
    private DraftAiAdviceService draftAiAdviceService;

    @Autowired
    private DraftService draftService;

    @Autowired
    private DraftAdminService draftAdminService;

    @Autowired
    private DraftLivePreviewRelayService draftLivePreviewRelayService;

    @Autowired
    private DraftLiveCommandService draftLiveCommandService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void currentPickerCanPublishDragPreview() {
        Long pickerId = createUser("preview-picker", "picker");
        Long candidateId = createUser("preview-candidate", "candidate");

        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "A", 1);
        assignPicker(teamId, pickerId);
        createCandidate(sessionId, candidateId, "candidate", "ZERG");
        createOrder(sessionId, 1L, teamId);
        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));
        clearInvocations(simpMessagingTemplate);

        draftLivePreviewRelayService.relayPreview(
                sessionId,
                previewPayload(DraftLivePreviewPhase.START, candidateId, 0.25d, 0.35d, 0.3d, 0.4d),
                new AuthActor(pickerId, "preview-picker", "ROLE_USER"),
                "ws-picker"
        );

        ArgumentCaptor<DraftLiveEventResponseDto> captor = ArgumentCaptor.forClass(DraftLiveEventResponseDto.class);
        verify(simpMessagingTemplate).convertAndSend(eq("/topic/drafts/" + sessionId), captor.capture());

        DraftLiveEventResponseDto event = captor.getValue();
        assertThat(event.getType()).isEqualTo(DraftLiveEventType.DRAG_PREVIEW);
        assertThat(event.getActorUserId()).isEqualTo(pickerId);
        assertThat(event.getPreview()).isNotNull();
        assertThat(event.getPreview().getPhase()).isEqualTo(DraftLivePreviewPhase.START);
        assertThat(event.getPreview().getCandidateUserId()).isEqualTo(candidateId);
        assertThat(event.getPreview().getCursorPosition().getX()).isEqualTo(0.25d);
        assertThat(event.getPreview().getCardPosition().getY()).isEqualTo(0.4d);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void nonPickerCannotPublishDragPreview() {
        Long pickerId = createUser("blocked-picker", "picker");
        Long otherUserId = createUser("blocked-other", "other");
        Long candidateId = createUser("blocked-candidate", "candidate");

        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "A", 1);
        assignPicker(teamId, pickerId);
        createCandidate(sessionId, candidateId, "candidate", "TERRAN");
        createOrder(sessionId, 1L, teamId);
        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));
        clearInvocations(simpMessagingTemplate);

        assertThatThrownBy(() -> draftLivePreviewRelayService.relayPreview(
                sessionId,
                previewPayload(DraftLivePreviewPhase.MOVE, candidateId, 0.2d, 0.2d, 0.25d, 0.25d),
                new AuthActor(otherUserId, "blocked-other", "ROLE_USER"),
                "ws-other"
        )).hasMessageContaining("current picker");

        verifyNoInteractions(simpMessagingTemplate);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pickPublishesTurnChangeCleanupBeforePickCompleted() {
        Long pickerAId = createUser("turn-picker-a", "pickerA");
        Long pickerBId = createUser("turn-picker-b", "pickerB");
        Long candidate1Id = createUser("turn-candidate-1", "candidate1");
        Long candidate2Id = createUser("turn-candidate-2", "candidate2");

        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        Long teamBId = createTeam(sessionId, "B", 2);
        assignPicker(teamAId, pickerAId);
        assignPicker(teamBId, pickerBId);
        createCandidate(sessionId, candidate1Id, "candidate1", "ZERG");
        createCandidate(sessionId, candidate2Id, "candidate2", "PROTOSS");
        createOrder(sessionId, 1L, teamAId);
        createOrder(sessionId, 2L, teamBId);
        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));
        clearInvocations(simpMessagingTemplate);

        draftLivePreviewRelayService.relayPreview(
                sessionId,
                previewPayload(DraftLivePreviewPhase.START, candidate1Id, 0.1d, 0.2d, 0.15d, 0.25d),
                new AuthActor(pickerAId, "turn-picker-a", "ROLE_USER"),
                "ws-turn"
        );
        clearInvocations(simpMessagingTemplate);

        draftLiveCommandService.pick(sessionId, candidate1Id, new AuthActor(pickerAId, "turn-picker-a", "ROLE_USER"));

        ArgumentCaptor<DraftLiveEventResponseDto> captor = ArgumentCaptor.forClass(DraftLiveEventResponseDto.class);
        verify(simpMessagingTemplate, times(2)).convertAndSend(eq("/topic/drafts/" + sessionId), captor.capture());

        List<DraftLiveEventResponseDto> events = captor.getAllValues();
        assertThat(events.get(0).getType()).isEqualTo(DraftLiveEventType.DRAG_PREVIEW);
        assertThat(events.get(0).getPreview().getPhase()).isEqualTo(DraftLivePreviewPhase.END);
        assertThat(events.get(0).getPreview().getEndReason()).isEqualTo(DraftLivePreviewEndReason.TURN_CHANGED);
        assertThat(events.get(0).getActorUserId()).isEqualTo(pickerAId);

        assertThat(events.get(1).getType()).isEqualTo(DraftLiveEventType.PICK_COMPLETED);
        assertThat(events.get(1).getSnapshot().getSession().getCurrentDraftTeamId()).isEqualTo(teamBId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void finishSessionPublishesCleanupBeforeSessionFinishedEvent() {
        Long pickerId = createUser("finish-picker", "picker");
        Long candidateId = createUser("finish-candidate", "candidate");

        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "A", 1);
        assignPicker(teamId, pickerId);
        createCandidate(sessionId, candidateId, "candidate", "RANDOM");
        createOrder(sessionId, 1L, teamId);
        draftLiveCommandService.startSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"));
        clearInvocations(simpMessagingTemplate);

        draftLivePreviewRelayService.relayPreview(
                sessionId,
                previewPayload(DraftLivePreviewPhase.START, candidateId, 0.4d, 0.5d, 0.45d, 0.55d),
                new AuthActor(pickerId, "finish-picker", "ROLE_USER"),
                "ws-finish"
        );
        clearInvocations(simpMessagingTemplate);

        draftLiveCommandService.finishSession(sessionId, new AuthActor(1L, "admin", "ROLE_ADMIN"), "manual");

        ArgumentCaptor<DraftLiveEventResponseDto> captor = ArgumentCaptor.forClass(DraftLiveEventResponseDto.class);
        verify(simpMessagingTemplate, times(2)).convertAndSend(eq("/topic/drafts/" + sessionId), captor.capture());

        List<DraftLiveEventResponseDto> events = captor.getAllValues();
        assertThat(events.get(0).getType()).isEqualTo(DraftLiveEventType.DRAG_PREVIEW);
        assertThat(events.get(0).getPreview().getPhase()).isEqualTo(DraftLivePreviewPhase.END);
        assertThat(events.get(0).getPreview().getEndReason()).isEqualTo(DraftLivePreviewEndReason.SESSION_FINISHED);
        assertThat(events.get(1).getType()).isEqualTo(DraftLiveEventType.SESSION_FINISHED);
    }

    private DraftLivePreviewPayloadDto previewPayload(
            DraftLivePreviewPhase phase,
            Long candidateUserId,
            double cursorX,
            double cursorY,
            double cardX,
            double cardY
    ) {
        return DraftLivePreviewPayloadDto.builder()
                .candidateUserId(candidateUserId)
                .phase(phase)
                .cursorPosition(DraftLiveNormalizedPositionDto.builder()
                        .x(cursorX)
                        .y(cursorY)
                        .build())
                .cardPosition(DraftLiveNormalizedPositionDto.builder()
                        .x(cardX)
                        .y(cardY)
                        .build())
                .build();
    }

    private Long createSession() {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle("preview relay session");
        requestDto.setStatus("READY");
        requestDto.setTeamCount(2);
        requestDto.setPickTimeSeconds(30);
        requestDto.setCurrentPickNo(1);
        return draftService.createSession(requestDto, adminActor()).getData().getId();
    }

    private Long createTeam(Long sessionId, String teamName, int displayOrder) {
        DraftTeamRequestDto requestDto = new DraftTeamRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setTeamName(teamName);
        requestDto.setDisplayOrder(displayOrder);
        return draftService.createTeam(requestDto, adminActor()).getData().getId();
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
        draftService.createCandidate(requestDto, adminActor());
    }

    private void createOrder(Long sessionId, Long pickNo, Long teamId) {
        DraftOrderRequestDto requestDto = new DraftOrderRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setDraftTeamId(teamId);
        draftService.createOrder(requestDto, adminActor());
    }

    private AuthActor adminActor() {
        return new AuthActor(1L, "admin", "ROLE_ADMIN");
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
