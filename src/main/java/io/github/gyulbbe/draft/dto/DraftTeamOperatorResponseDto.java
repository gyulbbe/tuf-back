package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftTeamOperatorResponseDto {
    private Long draftTeamId;
    private Long operatorUserId;
    private String operatorName;
    private String role;
    private String isActive;
    private String canPick;
}
