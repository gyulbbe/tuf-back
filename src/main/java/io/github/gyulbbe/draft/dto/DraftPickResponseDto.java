package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DraftPickResponseDto {
    private Long draftSessionId;
    private Long pickNo;
    private Long draftTeamId;
    private String draftTeamName;
    private Long candidateUserId;
    private String candidateUserLoginId;
    private String candidateName;
    private String tier;
    private String race;
    private Long pickedByUserId;
    private String pickedByUserLoginId;
    private String pickedByUserName;
    private LocalDateTime pickedAt;
}
