package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AdminPersonalLeagueResponseDto {
    private Long id;
    private String leagueName;
    private String seasonName;
    private String description;
    private String status;
    private String leagueType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long tournamentId;
    private String tournamentBracketType;
    private Integer tournamentBestOf;
    private Boolean canEditTournament;
    private List<AdminPersonalLeaguePlayerResponseDto> players = new ArrayList<>();
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
}
