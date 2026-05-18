package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdminLeagueSummaryResponseDto {
    private Long id;
    private String leagueName;
    private String seasonName;
    private String status;
    private String leagueType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long draftSessionId;
    private Long tournamentId;
    private String linkedType;
    private String linkedLabel;
    private Long teamCount;
    private Long participantCount;
    private Boolean canDelete;
    private String deleteBlockedReason;
    private LocalDateTime updateDate;
}
