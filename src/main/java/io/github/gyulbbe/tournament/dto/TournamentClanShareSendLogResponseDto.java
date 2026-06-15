package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TournamentClanShareSendLogResponseDto {
    private Long id;
    private Long tournamentId;
    private Long matchId;
    private String sendGroupId;
    private String player1;
    private String player2;
    private String winner;
    private String loser;
    private String mapName;
    private String matchType;
    private String matchName;
    private String playedDate;
    private String eloStatus;
    private String eloMessage;
    private String sheetStatus;
    private String sheetMessage;
    private Long requestedByUserId;
    private LocalDateTime regDate;
}
