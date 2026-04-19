package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftTeamOperatorRequestDto {
    private Long draftTeamId;
    private Long operatorUserId;
    private String role;
    private String isActive;
}
