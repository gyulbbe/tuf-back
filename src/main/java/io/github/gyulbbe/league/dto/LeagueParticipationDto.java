package io.github.gyulbbe.league.dto;

import lombok.Data;

@Data
public class LeagueParticipationDto {
    private Long id;
    private Long leagueId;
    private Long userId;
    private String race;
    private String status;
}
