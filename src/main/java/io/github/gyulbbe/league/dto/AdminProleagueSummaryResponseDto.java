package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdminProleagueSummaryResponseDto {
    private Long id;
    private String leagueName;
    private String seasonName;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long draftSessionId;
    private String draftStatus;
    private Boolean canEditDraft;
    private Long teamCount;
    private Long candidateCount;
    private LocalDateTime updateDate;
}
