package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LeagueDto {
    private Long id;
    private String leagueName;
    private LocalDate startDate;
    private LocalDate endDate;
}
