package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AdminProleagueHistoryResponseDto {
    private Long id;
    private String leagueName;
    private String seasonName;
    private String description;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long draftSessionId;
    private String draftStatus;
    private Long championTeamId;
    private String championTeamName;
    private Long runnerUpTeamId;
    private String runnerUpTeamName;
    private Long teamCount;
    private Long participantCount;
    private List<AdminProleagueHistoryTeamResponseDto> teams = new ArrayList<>();
    private LocalDateTime updateDate;
}
