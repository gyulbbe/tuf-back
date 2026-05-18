package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminProleagueTeamResponseDto {
    private Long id;
    private String teamName;
    private String leaderUserId;
    private String viceLeaderUserId;
    private String pickerUserId;
    private Integer displayOrder;
    private Long draftTeamId;
    private List<AdminProleagueTeamMemberResponseDto> members = new ArrayList<>();
}
