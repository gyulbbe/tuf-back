package io.github.gyulbbe.league.dto;

import lombok.Data;

@Data
public class AdminProleagueHistoryTeamResponseDto {
    private Long teamId;
    private String teamName;
    private String leaderUserId;
    private String viceLeaderUserId;
}
