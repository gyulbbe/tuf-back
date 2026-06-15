package io.github.gyulbbe.tournament.dto;

import lombok.Data;

@Data
public class TournamentClanShareSendLogRequestDto {
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
}
