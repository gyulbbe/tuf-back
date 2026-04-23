package io.github.gyulbbe.rpsdraft.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "rps_draft_sessions_seq_gen",
        sequenceName = "RPS_DRAFT_SESSIONS_SEQ",
        allocationSize = 1
)
@Table(
        name = "RPS_DRAFT_SESSIONS",
        indexes = {
                @Index(name = "idx_rps_draft_sessions_status", columnList = "STATUS"),
                @Index(name = "idx_rps_draft_sessions_owner_user", columnList = "OWNER_USER_ID"),
                @Index(name = "idx_rps_draft_sessions_current_team", columnList = "CURRENT_DRAFT_TEAM_ID"),
                @Index(name = "idx_rps_draft_sessions_pending_team", columnList = "PENDING_DRAFT_TEAM_ID")
        }
)
public class RpsDraftSessionEntity {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_RPS_PENDING = "RPS_PENDING";
    public static final String STATUS_PICKING = "PICKING";
    public static final String STATUS_FINISHED = "FINISHED";

    public static final String RPS_ROCK = "ROCK";
    public static final String RPS_PAPER = "PAPER";
    public static final String RPS_SCISSORS = "SCISSORS";

    public static final String RPS_RESULT_PENDING = "PENDING";
    public static final String RPS_RESULT_DRAW = "DRAW";
    public static final String RPS_RESULT_TEAM1_WIN = "TEAM1_WIN";
    public static final String RPS_RESULT_TEAM2_WIN = "TEAM2_WIN";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rps_draft_sessions_seq_gen")
    private Long id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "OWNER_USER_ID", nullable = false)
    private Long ownerUserId;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_READY;

    @Builder.Default
    @Column(name = "CURRENT_PICK_NO", nullable = false)
    private Integer currentPickNo = 1;

    @Column(name = "CURRENT_DRAFT_TEAM_ID")
    private Long currentDraftTeamId;

    @Column(name = "PENDING_DRAFT_TEAM_ID")
    private Long pendingDraftTeamId;

    @Column(name = "TEAM1_RPS_CHOICE")
    private String team1RpsChoice;

    @Column(name = "TEAM2_RPS_CHOICE")
    private String team2RpsChoice;

    @Builder.Default
    @Column(name = "RPS_RESULT", nullable = false)
    private String rpsResult = RPS_RESULT_PENDING;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;

    public void start(LocalDateTime now) {
        this.status = STATUS_RPS_PENDING;
        this.currentPickNo = 1;
        this.currentDraftTeamId = null;
        this.pendingDraftTeamId = null;
        this.team1RpsChoice = null;
        this.team2RpsChoice = null;
        this.rpsResult = RPS_RESULT_PENDING;
        this.startedAt = now;
        this.endedAt = null;
    }

    public void submitChoice(int displayOrder, String choice) {
        if (displayOrder == 1) {
            this.team1RpsChoice = choice;
            return;
        }
        this.team2RpsChoice = choice;
    }

    public boolean hasChoice(int displayOrder) {
        return displayOrder == 1 ? this.team1RpsChoice != null : this.team2RpsChoice != null;
    }

    public void resolveRpsRound(Long winnerTeamId, Long loserTeamId, String result) {
        this.currentDraftTeamId = winnerTeamId;
        this.pendingDraftTeamId = loserTeamId;
        this.rpsResult = result;
        this.status = STATUS_PICKING;
    }

    public void resetRpsRound() {
        this.currentDraftTeamId = null;
        this.pendingDraftTeamId = null;
        this.team1RpsChoice = null;
        this.team2RpsChoice = null;
        this.rpsResult = RPS_RESULT_PENDING;
        this.status = STATUS_RPS_PENDING;
    }

    public void advanceToPendingPick(int nextPickNo) {
        this.currentPickNo = nextPickNo;
        this.currentDraftTeamId = this.pendingDraftTeamId;
        this.pendingDraftTeamId = null;
        this.status = STATUS_PICKING;
    }

    public void prepareNextRpsRound(int nextPickNo) {
        this.currentPickNo = nextPickNo;
        resetRpsRound();
    }

    public void finish(LocalDateTime now) {
        this.status = STATUS_FINISHED;
        this.currentDraftTeamId = null;
        this.pendingDraftTeamId = null;
        this.endedAt = now;
    }

    public void clearProgressState() {
        this.currentDraftTeamId = null;
        this.pendingDraftTeamId = null;
        this.team1RpsChoice = null;
        this.team2RpsChoice = null;
        this.rpsResult = RPS_RESULT_PENDING;
    }
}
