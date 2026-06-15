package io.github.gyulbbe.tournament.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "tournament_clan_share_send_logs_seq_gen",
        sequenceName = "TOURNAMENT_CLAN_SHARE_SEND_LOGS_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_CLAN_SHARE_SEND_LOGS")
public class TournamentClanShareSendLogEntity {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_clan_share_send_logs_seq_gen")
    private Long id;

    @Column(name = "TOURNAMENT_ID", nullable = false)
    private Long tournamentId;

    @Column(name = "MATCH_ID", nullable = false)
    private Long matchId;

    @Column(name = "SEND_GROUP_ID", nullable = false, length = 36)
    private String sendGroupId;

    @Column(name = "PLAYER1", nullable = false)
    private String player1;

    @Column(name = "PLAYER2", nullable = false)
    private String player2;

    @Column(name = "WINNER", nullable = false)
    private String winner;

    @Column(name = "LOSER", nullable = false)
    private String loser;

    @Column(name = "MAP_NAME", nullable = false)
    private String mapName;

    @Column(name = "MATCH_TYPE", nullable = false, length = 50)
    private String matchType;

    @Column(name = "MATCH_NAME", nullable = false)
    private String matchName;

    @Column(name = "PLAYED_DATE", nullable = false, length = 20)
    private String playedDate;

    @Column(name = "ELO_STATUS", nullable = false, length = 20)
    private String eloStatus;

    @Column(name = "ELO_MESSAGE", length = 500)
    private String eloMessage;

    @Column(name = "SHEET_STATUS", nullable = false, length = 20)
    private String sheetStatus;

    @Column(name = "SHEET_MESSAGE", length = 500)
    private String sheetMessage;

    @Column(name = "REQUESTED_BY_USER_ID", nullable = false)
    private Long requestedByUserId;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @PrePersist
    void onCreate() {
        if (regDate == null) {
            regDate = LocalDateTime.now();
        }
    }
}
