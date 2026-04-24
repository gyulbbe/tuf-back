package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftOrderRequestDto {
    private Long draftSessionId;
    private Long roundNo;
    private Long pickNo;
    private Long draftTeamId;
}
