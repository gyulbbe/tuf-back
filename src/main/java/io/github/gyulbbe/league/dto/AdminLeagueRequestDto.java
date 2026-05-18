package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class AdminLeagueRequestDto {
    private String leagueName;
    private String seasonName;
    private String description;
    private String status;
    private String leagueType;
    private LocalDate startDate;
    private LocalDate endDate;

    private Boolean createDraft;
    private List<AdminProleagueTeamRequestDto> teams = new ArrayList<>();
    private AdminProleagueDraftRequestDto draft;

    private Boolean createTournament;
    private List<AdminPersonalLeaguePlayerRequestDto> players = new ArrayList<>();
    private AdminPersonalLeagueTournamentRequestDto tournament;

    private Integer totalGames;
    private List<AdminLeagueRaceSurvivalTeamRequestDto> raceTeams = new ArrayList<>();
}
