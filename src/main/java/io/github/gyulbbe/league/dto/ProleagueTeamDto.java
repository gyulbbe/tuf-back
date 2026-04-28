package io.github.gyulbbe.league.dto;

import lombok.Data;

@Data
public class ProleagueTeamDto {
    private Long id;
    private String teamName;
    private Long leagueId;
    private Long leaderId;
    private Long viceLeaderId;
}
