package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DraftCandidateRequestDto {
    private Long draftSessionId;
    private Long candidateUserId;
    private String candidateName;
    private String race;
    private String status;
    private Long pickedDraftTeamId;
    private LocalDateTime pickedAt;
}
