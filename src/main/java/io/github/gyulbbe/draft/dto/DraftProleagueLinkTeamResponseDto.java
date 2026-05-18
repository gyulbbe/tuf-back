package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftProleagueLinkTeamResponseDto {
    private Long proleagueTeamId;
    private String teamName;
    private Integer displayOrder;
    private Long leaderUserId;
    private String leaderUserLoginId;
    private Long viceLeaderUserId;
    private String viceLeaderUserLoginId;
}
