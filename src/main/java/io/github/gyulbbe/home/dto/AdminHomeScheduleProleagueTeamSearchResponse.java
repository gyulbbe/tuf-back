package io.github.gyulbbe.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminHomeScheduleProleagueTeamSearchResponse {
    private Long teamId;
    private String teamName;
    private Long leagueId;
    private String leagueName;
    private String seasonName;
}
