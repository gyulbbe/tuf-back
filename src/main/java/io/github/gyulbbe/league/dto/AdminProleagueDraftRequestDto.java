package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminProleagueDraftRequestDto {
    private Integer teamCount;
    private Integer pickTimeSeconds;
    private String orderMode;
    private List<AdminProleagueTeamRequestDto> teams = new ArrayList<>();
    private List<AdminProleagueCandidateRequestDto> candidates = new ArrayList<>();
}
