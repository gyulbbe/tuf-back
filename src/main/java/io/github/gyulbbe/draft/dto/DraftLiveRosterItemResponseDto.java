package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DraftLiveRosterItemResponseDto {
    private Long pickNo;
    private Long roundNo;
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
