package io.github.gyulbbe.tournament.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
        name = "tournament_matches_seq_gen",
        sequenceName = "TOURNAMENT_MATCHES_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_MATCHES")
public class TournamentMatchEntity {

    public static final String ROLE_OPENING = "OPENING";
    public static final String ROLE_WINNERS = "WINNERS";
    public static final String ROLE_LOSERS = "LOSERS";
    public static final String ROLE_DECIDER = "DECIDER";
    public static final String ROLE_ROUND = "ROUND";
    public static final String ROLE_FINAL = "FINAL";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_matches_seq_gen")
    private Long id;

    @Column(name = "STAGE_ID", nullable = false)
    private Long stageId;

    @Column(name = "GROUP_ID", nullable = false)
    private Long groupId;

    @Column(name = "MATCH_KEY", nullable = false)
    private String matchKey;

    @Column(name = "MATCH_ROLE", nullable = false)
    private String matchRole;

    @Column(name = "ROUND_NO")
    private Integer roundNo;

    @Column(name = "MATCH_NO")
    private Integer matchNo;

    @Column(name = "DISPLAY_NAME", nullable = false)
    private String displayName;

    @Builder.Default
    @Column(name = "BEST_OF", nullable = false)
    private Integer bestOf = 3;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "WINNER_PARTICIPANT_ID")
    private Long winnerParticipantId;

    @Column(name = "MAP_ID")
    private Long mapId;

    @Column(name = "SCHEDULED_AT")
    private LocalDateTime scheduledAt;

    @Column(name = "LAYOUT_COL")
    private Integer layoutCol;

    @Column(name = "LAYOUT_ROW")
    private Integer layoutRow;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public void markReady() {
        this.status = STATUS_READY;
    }

    public void markPending() {
        this.status = STATUS_PENDING;
    }

    public void finish(Long winnerParticipantId) {
        this.status = STATUS_FINISHED;
        this.winnerParticipantId = winnerParticipantId;
    }

    public void cancel() {
        this.status = STATUS_CANCELLED;
    }

    public void assignMap(Long mapId) {
        this.mapId = mapId;
    }

    public void updateBestOf(Integer bestOf) {
        this.bestOf = bestOf;
    }

    public void reschedule(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public void updateLayout(Integer layoutCol, Integer layoutRow) {
        this.layoutCol = layoutCol;
        this.layoutRow = layoutRow;
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
