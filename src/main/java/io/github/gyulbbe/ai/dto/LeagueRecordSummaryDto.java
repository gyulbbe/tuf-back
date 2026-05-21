package io.github.gyulbbe.ai.dto;

import lombok.Data;

@Data
public class LeagueRecordSummaryDto {
    private Long leagueId;
    private String leagueName;
    private String leagueType;
    private Long playerCount;
    private Integer totalWins;
    private Integer totalLosses;
    private Integer totalDraws;
    private Integer totalScoreFor;
    private Integer totalScoreAgainst;
}
