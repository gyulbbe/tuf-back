package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntrySubmissionTeamResponseDto {
    private Long id;
    private Long entrySubmissionSessionId;
    private String teamName;
    private Integer displayOrder;
    private Long captainUserId;
    private String captainUserLoginId;
    private boolean submitted;
    private LocalDateTime submittedAt;
}
