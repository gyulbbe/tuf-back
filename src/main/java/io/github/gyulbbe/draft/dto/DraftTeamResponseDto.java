package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftTeamResponseDto {
    private Long id;
    private Long draftSessionId;
    private String teamName;
    private Integer displayOrder;
    private Long pickerUserId;
    private String pickerName;
}
