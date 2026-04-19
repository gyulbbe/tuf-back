package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftTeamRequestDto {
    private Long draftSessionId;
    private String teamName;
    private Integer displayOrder;
}
