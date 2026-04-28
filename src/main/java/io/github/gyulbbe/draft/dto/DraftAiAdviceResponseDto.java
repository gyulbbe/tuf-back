package io.github.gyulbbe.draft.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DraftAiAdviceResponseDto {
    private Long pickNo;
    private Long evaluatedTeamId;
    private String evaluatedTeamName;
    private Long evaluatedCandidateUserId;
    private String evaluatedCandidateName;
    private Long nextPickNo;
    private Long recommendedTeamId;
    private String recommendedTeamName;
    private Long recommendedCandidateUserId;
    private String recommendedCandidateName;
    private String message;
}
