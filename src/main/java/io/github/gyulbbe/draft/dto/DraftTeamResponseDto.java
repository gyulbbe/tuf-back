package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftTeamResponseDto {
    private Long id;
    private Long draftSessionId;
    private Long proleagueTeamId;
    private String teamName;
    private Integer displayOrder;
    private Long pickerUserId;
    private String pickerUserLoginId;
    private String pickerName;
}
