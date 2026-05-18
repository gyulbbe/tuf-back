package io.github.gyulbbe.league.dto;

import lombok.Data;

@Data
public class AdminProleagueTeamMemberRequestDto {
    private String userId;
    private Integer displayOrder;
}
