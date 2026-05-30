package io.github.gyulbbe.entrysubmission.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EntrySubmissionEventResponseDto {
    private EntrySubmissionEventType type;
    private Long sessionId;
    private LocalDateTime occurredAt;
    private LocalDateTime serverNow;
    private Long actorUserId;
    private String actorUserLoginId;
    private String message;
    private EntrySubmissionSnapshotResponseDto snapshot;
}
