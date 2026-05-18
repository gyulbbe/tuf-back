package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminLeagueRaceSurvivalTeamRequestDto {
    private String race;
    private List<AdminPersonalLeaguePlayerRequestDto> players = new ArrayList<>();
}
