package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class AdminProleagueCreateRequestDto {
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
}
