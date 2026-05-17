package io.github.gyulbbe.league.dto;

import lombok.Data;

@Data
public class AdminProleagueTeamRequestDto {
    private String teamName;
    private String leaderUserId;
    private String viceLeaderUserId;
    private Integer displayOrder;
}
