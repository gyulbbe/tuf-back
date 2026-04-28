package io.github.gyulbbe.draft.ws;

import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftAiAdviceResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveEventResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveEventType;
import io.github.gyulbbe.draft.dto.DraftLivePreviewPayloadDto;
import io.github.gyulbbe.draft.service.DraftSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftEventPublisher {

    private static final String TOPIC_PREFIX = "/topic/drafts/";

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final DraftSnapshotService draftSnapshotService;

    public void publishAfterCommit(Long sessionId, DraftLiveEventType type, AuthActor actor, String message) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(sessionId, type, actor, message);
                }
            });
            return;
        }

        publish(sessionId, type, actor, message);
    }

    public void publish(Long sessionId, DraftLiveEventType type, AuthActor actor, String message) {
        LocalDateTime now = LocalDateTime.now();
        DraftLiveEventResponseDto event = DraftLiveEventResponseDto.builder()
                .type(type)
                .sessionId(sessionId)
                .occurredAt(now)
                .serverNow(now)
                .actorUserId(actor != null ? actor.userPk() : null)
                .actorUserLoginId(actor != null ? actor.username() : null)
                .message(message)
                .snapshot(draftSnapshotService.getBroadcastSnapshot(sessionId))
                .build();

        simpMessagingTemplate.convertAndSend(TOPIC_PREFIX + sessionId, event);
    }

    public void publishPreview(Long sessionId, AuthActor actor, DraftLivePreviewPayloadDto preview) {
        LocalDateTime now = LocalDateTime.now();
        DraftLiveEventResponseDto event = DraftLiveEventResponseDto.builder()
                .type(DraftLiveEventType.DRAG_PREVIEW)
                .sessionId(sessionId)
                .occurredAt(now)
                .serverNow(now)
                .actorUserId(actor != null ? actor.userPk() : null)
                .actorUserLoginId(actor != null ? actor.username() : null)
                .preview(preview)
                .build();

        simpMessagingTemplate.convertAndSend(TOPIC_PREFIX + sessionId, event);
    }

    public void publishAiAdvice(Long sessionId, DraftLiveEventType type, DraftAiAdviceResponseDto aiAdvice) {
        LocalDateTime now = LocalDateTime.now();
        DraftLiveEventResponseDto event = DraftLiveEventResponseDto.builder()
                .type(type)
                .sessionId(sessionId)
                .occurredAt(now)
                .serverNow(now)
                .message(aiAdvice != null ? aiAdvice.getMessage() : null)
                .aiAdvice(aiAdvice)
                .build();

        simpMessagingTemplate.convertAndSend(TOPIC_PREFIX + sessionId, event);
    }
}
