package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminProleagueFinishRequestDto {
    private String championTeamName;
    private String runnerUpTeamName;
    private LocalDate endDate;
}
