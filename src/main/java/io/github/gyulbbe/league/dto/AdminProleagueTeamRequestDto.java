package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminProleagueTeamRequestDto {
    private String teamName;
    private String leaderUserId;
    private String viceLeaderUserId;
    private String pickerUserId;
    private Integer displayOrder;
    private List<AdminProleagueTeamMemberRequestDto> members = new ArrayList<>();
}
