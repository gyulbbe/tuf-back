package io.github.gyulbbe.ai.dto;

import lombok.Data;

@Data
public class UserLeagueRecordDto {
    private Long leagueId;
    private String leagueName;
    private String leagueType;
    private Long userId;
    private String loginId;
    private Integer wins;
    private Integer losses;
    private Integer draws;
    private Integer scoreFor;
    private Integer scoreAgainst;
    private String sourceType;
}
