package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DraftCandidateResponseDto {
    private Long draftSessionId;
    private Long candidateUserId;
    private String candidateUserLoginId;
    private String candidateName;
    private String tier;
    private String race;
    private String status;
    private Long pickedDraftTeamId;
    private String pickedDraftTeamName;
    private LocalDateTime pickedAt;
}
