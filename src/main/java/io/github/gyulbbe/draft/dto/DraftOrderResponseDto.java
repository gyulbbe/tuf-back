package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftOrderResponseDto {
    private Long draftSessionId;
    private Long roundNo;
    private Long pickNo;
    private Long draftTeamId;
    private String draftTeamName;
}
