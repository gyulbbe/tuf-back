package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntrySubmissionEntryResponseDto {
    private Long entrySubmissionSessionId;
    private Long entrySubmissionTeamId;
    private Integer setNo;
    private Long playerId;
    private String playerName;
    private Long submittedByUserId;
    private String submittedByUserLoginId;
    private LocalDateTime submittedAt;
}
