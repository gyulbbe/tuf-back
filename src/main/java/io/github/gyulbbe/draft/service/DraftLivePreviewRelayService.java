package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftLiveNormalizedPositionDto;
import io.github.gyulbbe.draft.dto.DraftLivePreviewEndReason;
import io.github.gyulbbe.draft.dto.DraftLivePreviewPayloadDto;
import io.github.gyulbbe.draft.dto.DraftLivePreviewPhase;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.ws.DraftEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DraftLivePreviewRelayService {

    private final DraftSessionRepository draftSessionRepository;
    private final DraftPermissionService draftPermissionService;
    private final DraftEventPublisher draftEventPublisher;

    private final Map<Long, ActivePreviewState> activePreviewBySessionId = new ConcurrentHashMap<>();

    public void relayPreview(
            Long sessionId,
            DraftLivePreviewPayloadDto payload,
            AuthActor actor,
            String connectionSessionId
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("Preview payload is required.");
        }

        DraftLivePreviewPhase phase = requirePhase(payload.getPhase());
        ActivePreviewState currentState = activePreviewBySessionId.get(sessionId);

        if (DraftLivePreviewPhase.END.equals(phase)) {
            assertTerminalPreviewAllowed(sessionId, actor, currentState);
            publishTerminalPreview(sessionId, payload, actor);
            return;
        }

        assertActivePreviewAllowed(sessionId, actor);

        Long candidateUserId = requireCandidateUserId(payload.getCandidateUserId());
        DraftLiveNormalizedPositionDto cursorPosition = sanitizeRequiredPosition(payload.getCursorPosition(), "cursorPosition");
        DraftLiveNormalizedPositionDto cardPosition = sanitizeRequiredPosition(payload.getCardPosition(), "cardPosition");

        activePreviewBySessionId.put(
                sessionId,
                new ActivePreviewState(connectionSessionId, actor.userPk(), candidateUserId)
        );

        draftEventPublisher.publishPreview(
                sessionId,
                actor,
                DraftLivePreviewPayloadDto.builder()
                        .candidateUserId(candidateUserId)
                        .phase(phase)
                        .cursorPosition(cursorPosition)
                        .cardPosition(cardPosition)
                        .build()
        );
    }

    public void clearPreviewAfterCommit(Long sessionId, DraftLivePreviewEndReason endReason) {
        runAfterCommit(() -> clearPreview(sessionId, endReason));
    }

    public void clearPreview(Long sessionId, DraftLivePreviewEndReason endReason) {
        ActivePreviewState removedState = activePreviewBySessionId.remove(sessionId);
        if (removedState == null) {
            return;
        }

        publishCleanupPreview(sessionId, removedState.actorUserId(), removedState.candidateUserId(), endReason);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String connectionSessionId = accessor.getSessionId();
        if (connectionSessionId == null || connectionSessionId.isBlank()) {
            return;
        }

        List<Long> targetSessionIds = new ArrayList<>();
        for (Map.Entry<Long, ActivePreviewState> entry : activePreviewBySessionId.entrySet()) {
            if (Objects.equals(entry.getValue().connectionSessionId(), connectionSessionId)) {
                targetSessionIds.add(entry.getKey());
            }
        }

        for (Long targetSessionId : targetSessionIds) {
            clearPreviewIfOwnedByConnection(targetSessionId, connectionSessionId);
        }
    }

    private void clearPreviewIfOwnedByConnection(Long sessionId, String connectionSessionId) {
        ActivePreviewState currentState = activePreviewBySessionId.get(sessionId);
        if (currentState == null || !Objects.equals(currentState.connectionSessionId(), connectionSessionId)) {
            return;
        }

        if (activePreviewBySessionId.remove(sessionId, currentState)) {
            publishCleanupPreview(
                    sessionId,
                    currentState.actorUserId(),
                    currentState.candidateUserId(),
                    DraftLivePreviewEndReason.DISCONNECTED
            );
        }
    }

    private void publishTerminalPreview(
            Long sessionId,
            DraftLivePreviewPayloadDto payload,
            AuthActor actor
    ) {
        Long actorUserId = actor.userPk();
        Long candidateUserId = payload.getCandidateUserId();

        ActivePreviewState removedState = activePreviewBySessionId.remove(sessionId);
        if (removedState != null) {
            actorUserId = removedState.actorUserId();
            if (candidateUserId == null) {
                candidateUserId = removedState.candidateUserId();
            }
        }

        if (candidateUserId == null) {
            throw new IllegalArgumentException("Candidate user id is required.");
        }

        DraftLivePreviewEndReason endReason = payload.getEndReason() != null
                ? payload.getEndReason()
                : DraftLivePreviewEndReason.RELEASED;

        publishCleanupPreview(sessionId, actorUserId, candidateUserId, endReason);
    }

    private void publishCleanupPreview(Long sessionId, Long actorUserId, Long candidateUserId, DraftLivePreviewEndReason endReason) {
        draftEventPublisher.publishPreview(
                sessionId,
                new AuthActor(actorUserId, "draft-preview", "ROLE_USER"),
                DraftLivePreviewPayloadDto.builder()
                        .candidateUserId(candidateUserId)
                        .phase(DraftLivePreviewPhase.END)
                        .endReason(endReason)
                        .build()
        );
    }

    private void assertActivePreviewAllowed(Long sessionId, AuthActor actor) {
        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        DraftSessionEntity session = loadSession(sessionId);
        if (!"LIVE".equals(session.getStatus())) {
            throw new IllegalArgumentException("Drag preview can only be sent while the session is LIVE.");
        }

        Long currentDraftTeamId = session.getCurrentDraftTeamId();
        if (currentDraftTeamId == null || !draftPermissionService.canPickForTeam(currentDraftTeamId, actor.userPk())) {
            throw new IllegalArgumentException("Only the current picker can send drag preview.");
        }
    }

    private void assertTerminalPreviewAllowed(Long sessionId, AuthActor actor, ActivePreviewState currentState) {
        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        if (currentState != null && Objects.equals(currentState.actorUserId(), actor.userPk())) {
            return;
        }

        assertActivePreviewAllowed(sessionId, actor);
    }

    private DraftSessionEntity loadSession(Long sessionId) {
        return draftSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Draft session not found."));
    }

    private DraftLivePreviewPhase requirePhase(DraftLivePreviewPhase phase) {
        if (phase == null) {
            throw new IllegalArgumentException("Preview phase is required.");
        }
        return phase;
    }

    private Long requireCandidateUserId(Long candidateUserId) {
        if (candidateUserId == null) {
            throw new IllegalArgumentException("Candidate user id is required.");
        }
        return candidateUserId;
    }

    private DraftLiveNormalizedPositionDto sanitizeRequiredPosition(
            DraftLiveNormalizedPositionDto position,
            String fieldName
    ) {
        if (position == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        return DraftLiveNormalizedPositionDto.builder()
                .x(sanitizeCoordinate(position.getX(), fieldName + ".x"))
                .y(sanitizeCoordinate(position.getY(), fieldName + ".y"))
                .build();
    }

    private Double sanitizeCoordinate(Double value, String fieldName) {
        if (value == null || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1.");
        }
        return value;
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }

        action.run();
    }

    private record ActivePreviewState(
            String connectionSessionId,
            Long actorUserId,
            Long candidateUserId
    ) {
    }
}
