package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

@Data
public class EntrySubmissionPlayerResponseDto {
    private Long id;
    private Long entrySubmissionSessionId;
    private Long entrySubmissionTeamId;
    private String playerName;
    private Integer displayOrder;
    private boolean captain;
}
