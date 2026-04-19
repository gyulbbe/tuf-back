package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DraftPickResponseDto {
    private Long draftSessionId;
    private Integer roundNo;
    private Long pickNo;
    private Long draftTeamId;
    private String draftTeamName;
    private Long candidateUserId;
    private String candidateName;
    private Long pickedByUserId;
    private String pickedByUserName;
    private LocalDateTime pickedAt;
}
