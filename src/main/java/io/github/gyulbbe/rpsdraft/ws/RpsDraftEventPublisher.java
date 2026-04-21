package io.github.gyulbbe.rpsdraft.ws;

import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveEventResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveEventType;
import io.github.gyulbbe.rpsdraft.service.RpsDraftSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RpsDraftEventPublisher {

    private static final String TOPIC_PREFIX = "/topic/rps-drafts/";

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
        RpsDraftLiveEventResponseDto event = RpsDraftLiveEventResponseDto.builder()
                .type(type)
                .sessionId(sessionId)
                .occurredAt(now)
                .serverNow(now)
                .actorUserId(actor != null ? actor.userPk() : null)
                .message(message)
                .roundResult(roundResult)
                .snapshot(rpsDraftSnapshotService.getBroadcastSnapshot(sessionId))
                .build();
        simpMessagingTemplate.convertAndSend(TOPIC_PREFIX + sessionId, event);
    }
}
