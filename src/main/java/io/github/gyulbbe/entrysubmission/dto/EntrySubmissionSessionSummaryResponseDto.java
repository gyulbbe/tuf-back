package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntrySubmissionSessionSummaryResponseDto {
    private Long id;
    private String title;
    private Long ownerUserId;
    private String ownerUserLoginId;
    private Long sourceRpsDraftSessionId;
    private String status;
    private Integer setCount;
    private LocalDateTime completedAt;
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
}
