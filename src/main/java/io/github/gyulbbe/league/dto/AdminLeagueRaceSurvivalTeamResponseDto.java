package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminLeagueRaceSurvivalTeamResponseDto {
    private String race;
    private List<AdminPersonalLeaguePlayerResponseDto> players = new ArrayList<>();
}
