package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

import java.util.List;

@Data
public class EntrySubmissionSessionCreateRequestDto {
    private String title;
    private Long team1CaptainUserId;
    private Long team2CaptainUserId;
    private Long sourceRpsDraftSessionId;
    private Boolean allowDuplicateSource;
    private List<String> team1PlayerNames;
    private List<String> team2PlayerNames;
    private Integer setCount;
}
