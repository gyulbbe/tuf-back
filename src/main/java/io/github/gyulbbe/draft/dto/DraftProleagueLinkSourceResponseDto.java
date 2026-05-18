package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DraftProleagueLinkSourceResponseDto {
    private Long leagueId;
    private String leagueName;
    private String seasonName;
    private String status;
    private List<DraftProleagueLinkTeamResponseDto> teams = new ArrayList<>();
    private List<DraftProleagueLinkExcludedUserResponseDto> excludedUsers = new ArrayList<>();
}
