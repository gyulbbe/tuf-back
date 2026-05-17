package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminProleagueCreateRequestDto {
    private String leagueName;
    private String seasonName;
    private String description;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean createDraft;
    private AdminProleagueDraftRequestDto draft;
}
