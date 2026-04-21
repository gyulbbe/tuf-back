package io.github.gyulbbe.rpsdraft.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RpsDraftLiveEventResponseDto {
    private RpsDraftLiveEventType type;
    private Long sessionId;
    private LocalDateTime occurredAt;
    private LocalDateTime serverNow;
    private Long actorUserId;
    private String message;
    private String roundResult;
    private RpsDraftLiveSnapshotResponseDto snapshot;
}
