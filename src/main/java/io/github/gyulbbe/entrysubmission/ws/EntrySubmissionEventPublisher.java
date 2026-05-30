package io.github.gyulbbe.entrysubmission.ws;

import io.github.gyulbbe.entrysubmission.auth.EntrySubmissionActor;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionEventResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionEventType;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSnapshotResponseDto;
import io.github.gyulbbe.entrysubmission.service.EntrySubmissionSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class EntrySubmissionEventPublisher {

    private static final String TOPIC_PREFIX = "/topic/entry-submissions/";

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final EntrySubmissionSnapshotService entrySubmissionSnapshotService;

    public void publishAfterCommit(
            Long sessionId,
            EntrySubmissionEventType type,
            EntrySubmissionActor actor,
            String message
    ) {
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

    public void publish(
            Long sessionId,
            EntrySubmissionEventType type,
            EntrySubmissionActor actor,
            String message
    ) {
        LocalDateTime now = LocalDateTime.now();
        EntrySubmissionSnapshotResponseDto snapshot = entrySubmissionSnapshotService.getBroadcastSnapshot(sessionId);
        EntrySubmissionEventResponseDto event = EntrySubmissionEventResponseDto.builder()
                .type(type)
                .sessionId(sessionId)
                .occurredAt(now)
                .serverNow(now)
                .actorUserId(actor != null ? actor.userPk() : null)
                .actorUserLoginId(actor != null ? actor.username() : null)
                .message(message)
                .snapshot(snapshot)
                .build();
        simpMessagingTemplate.convertAndSend(TOPIC_PREFIX + sessionId, event);
    }
}
