package io.github.gyulbbe.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserMatchResultDto {
    private Long leagueId;
    private String leagueName;
    private String leagueType;
    private Long tournamentId;
    private Long matchId;
    private Long slotId;
    private Long userId;
    private String loginId;
    private Long participantId;
    private Long opponentUserId;
    private String opponentLoginId;
    private String result;
    private Integer scoreFor;
    private Integer scoreAgainst;
    private Integer isBye;
    private LocalDateTime decidedAt;
    private String sourceType;
}
