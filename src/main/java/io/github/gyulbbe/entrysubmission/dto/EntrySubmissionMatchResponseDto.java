package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

@Data
public class EntrySubmissionMatchResponseDto {
    private Integer setNo;
    private Long team1PlayerId;
    private String team1PlayerName;
    private Long team2PlayerId;
    private String team2PlayerName;
}
