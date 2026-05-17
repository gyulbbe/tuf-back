package io.github.gyulbbe.home.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
        name = "home_schedule_match_players_seq_gen",
        sequenceName = "HOME_SCHEDULE_MATCH_PLAYERS_SEQ",
        allocationSize = 1
)
@Table(name = "HOME_SCHEDULE_MATCH_PLAYERS")
public class HomeScheduleMatchPlayerEntity {

    public static final String SIDE_A = "A";
    public static final String SIDE_B = "B";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "home_schedule_match_players_seq_gen")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MATCH_ID", nullable = false)
    private HomeScheduleMatchEntity match;

    @Column(name = "MATCH_ID", insertable = false, updatable = false)
    private Long matchId;

    @Column(name = "SIDE", nullable = false, length = 1)
    private String side;

    @Column(name = "SLOT_ORDER", nullable = false)
    private Integer slotOrder;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "PLAYER_NAME", length = 100)
    private String playerName;

    @Column(name = "PLAYER_RANK", length = 20)
    private String playerRank;

    @Column(name = "PLAYER_RACE", length = 20)
    private String playerRace;

    @Column(name = "NOTE", length = 300)
    private String note;

    @Column(name = "REG_DATE", nullable = false, updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE", nullable = false)
    private LocalDateTime updateDate;

    public void attachMatch(HomeScheduleMatchEntity match) {
        this.match = match;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (regDate == null) {
            regDate = now;
        }
        if (updateDate == null) {
            updateDate = regDate;
        }
    }

    @PreUpdate
    void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
