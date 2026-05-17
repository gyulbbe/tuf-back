package io.github.gyulbbe.league.dto;

import lombok.Data;

@Data
public class AdminProleagueTeamResponseDto {
    private Long id;
    private String teamName;
    private String leaderUserId;
    private String viceLeaderUserId;
    private Integer displayOrder;
    private Long draftTeamId;
}
