package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AdminProleagueResponseDto {
    private Long id;
    private String leagueName;
    private String seasonName;
    private String description;
    private String status;
    private String leagueType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long draftSessionId;
    private String draftStatus;
    private String draftOrderMode;
    private Integer draftTeamCount;
    private Integer draftPickTimeSeconds;
    private Boolean canEditDraft;
    private Long championTeamId;
    private Long runnerUpTeamId;
    private List<AdminProleagueTeamResponseDto> teams = new ArrayList<>();
    private List<AdminProleagueCandidateResponseDto> candidates = new ArrayList<>();
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
}
