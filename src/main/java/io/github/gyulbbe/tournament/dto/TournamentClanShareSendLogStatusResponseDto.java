package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TournamentClanShareSendLogStatusResponseDto {
    private List<Group> groups;
    private Totals totals;

    @Data
    @Builder
    public static class Group {
        private String groupKey;
        private String groupLabel;
        private List<Match> matches;
    }

    @Data
    @Builder
    public static class Match {
        private Long matchId;
        private String player1;
        private String player2;
        private String winner;
        private String mapName;
        private String status;
        private String eloMessage;
        private String sheetStatus;
        private String sheetMessage;
        private LocalDateTime latestSentAt;
        private boolean retryable;
        private List<SetStatus> sets;
    }

    @Data
    @Builder
    public static class SetStatus {
        private Integer setNo;
        private String status;
        private String eloMessage;
        private String sheetStatus;
        private String sheetMessage;
        private LocalDateTime latestSentAt;
        private boolean retryable;
    }

    @Data
    @Builder
    public static class Totals {
        private int total;
        private int success;
        private int failed;
        private int unsent;
        private int sheetFailed;
        private int retryable;
    }
}
