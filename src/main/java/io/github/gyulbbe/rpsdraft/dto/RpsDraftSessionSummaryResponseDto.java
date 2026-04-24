package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RpsDraftSessionSummaryResponseDto {
    private Long id;
    private String title;
    private Long ownerUserId;
    private String ownerUserLoginId;
    private String ownerName;
    private String status;
    private Integer currentPickNo;
    private Long currentDraftTeamId;
    private Long pendingDraftTeamId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
