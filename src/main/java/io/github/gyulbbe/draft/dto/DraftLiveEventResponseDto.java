package io.github.gyulbbe.draft.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DraftLiveEventResponseDto {
    private DraftLiveEventType type;
    private Long sessionId;
    private LocalDateTime occurredAt;
    private LocalDateTime serverNow;
    private Long actorUserId;
    private String actorUserLoginId;
    private String message;
    private DraftLiveSnapshotResponseDto snapshot;
    private DraftLivePreviewPayloadDto preview;
}
