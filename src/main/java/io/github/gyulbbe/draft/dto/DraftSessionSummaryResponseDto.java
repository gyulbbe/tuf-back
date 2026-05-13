package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DraftSessionSummaryResponseDto {
    private Long id;
    private String title;
    private Long ownerUserId;
    private String ownerUserLoginId;
    private String ownerName;
    private String status;
    private String orderMode;
    private Integer teamCount;
    private Integer pickTimeSeconds;
    private Long pickedCount;
    private Integer currentPickNo;
    private Long currentDraftTeamId;
    private LocalDateTime deadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
