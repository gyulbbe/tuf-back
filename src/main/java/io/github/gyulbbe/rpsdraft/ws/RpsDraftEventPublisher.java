package io.github.gyulbbe.rpsdraft.ws;

import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveEventResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveEventType;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSnapshotResponseDto;
import io.github.gyulbbe.rpsdraft.service.RpsDraftSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RpsDraftEventPublisher {

    private static final String TOPIC_PREFIX = "/topic/rps-drafts/";
    private static final Set<RpsDraftLiveEventType> SNAPSHOT_REQUIRED_EVENT_TYPES = EnumSet.of(
            RpsDraftLiveEventType.RPS_SUBMITTED,
            RpsDraftLiveEventType.RPS_RESOLVED,
            RpsDraftLiveEventType.TURN_CHANGED,
            RpsDraftLiveEventType.PICK_COMPLETED,
            RpsDraftLiveEventType.SESSION_FINISHED
    );

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final RpsDraftSnapshotService rpsDraftSnapshotService;

    public void publishAfterCommit(
            Long sessionId,
            RpsDraftLiveEventType type,
            RpsDraftActor actor,
            String message,
            String roundResult
    ) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(sessionId, type, actor, message, roundResult);
                }
            });
            return;
        }

        publish(sessionId, type, actor, message, roundResult);
    }

    public void publish(
            Long sessionId,
            RpsDraftLiveEventType type,
            RpsDraftActor actor,
            String message,
            String roundResult
    ) {
        LocalDateTime now = LocalDateTime.now();
        RpsDraftLiveSnapshotResponseDto snapshot = buildSnapshot(sessionId, type);
        RpsDraftLiveEventResponseDto event = RpsDraftLiveEventResponseDto.builder()
                .type(type)
                .sessionId(sessionId)
                .occurredAt(now)
                .serverNow(now)
                .actorUserId(actor != null ? actor.userPk() : null)
                .actorUserLoginId(actor != null ? actor.username() : null)
                .message(message)
                .roundResult(roundResult)
                .snapshot(snapshot)
                .build();
        simpMessagingTemplate.convertAndSend(TOPIC_PREFIX + sessionId, event);
    }

    private RpsDraftLiveSnapshotResponseDto buildSnapshot(Long sessionId, RpsDraftLiveEventType type) {
        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getBroadcastSnapshot(sessionId);
        if (SNAPSHOT_REQUIRED_EVENT_TYPES.contains(type) && snapshot == null) {
            throw new IllegalStateException("RPS draft event snapshot is required.");
        }
        return snapshot;
    }
}
