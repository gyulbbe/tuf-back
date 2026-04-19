package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DraftLiveSessionInfoResponseDto {
    private Long id;
    private String title;
    private String status;
    private Integer teamCount;
    private Integer pickTimeSeconds;
    private Integer currentPickNo;
    private Long currentDraftTeamId;
    private LocalDateTime deadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime serverNow;
}
