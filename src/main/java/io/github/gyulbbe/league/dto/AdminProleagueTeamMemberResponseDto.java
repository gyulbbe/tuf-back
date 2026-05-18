package io.github.gyulbbe.league.dto;

import lombok.Data;

@Data
public class AdminProleagueTeamMemberResponseDto {
    private Long id;
    private String userId;
    private String race;
    private String source;
    private String status;
    private Integer displayOrder;
}
