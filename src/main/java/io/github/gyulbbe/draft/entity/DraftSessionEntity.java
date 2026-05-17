package io.github.gyulbbe.draft.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "draft_sessions_seq_gen",
        sequenceName = "DRAFT_SESSIONS_SEQ",
        allocationSize = 1
)
@Table(
        name = "DRAFT_SESSIONS",
        indexes = {
                @Index(name = "idx_draft_sessions_status", columnList = "STATUS"),
                @Index(name = "idx_draft_sessions_owner_user", columnList = "OWNER_USER_ID")
        }
)
public class DraftSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "draft_sessions_seq_gen")
    private Long id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "OWNER_USER_ID")
    private Long ownerUserId;

    @Column(name = "STATUS", nullable = false)
    private String status;

    @Builder.Default
    @Column(name = "ORDER_MODE", nullable = false)
    private String orderMode = "BASIC";

    @Column(name = "TEAM_COUNT", nullable = false)
    private Integer teamCount;

    @Column(name = "PICK_TIME_SECONDS", nullable = false)
    private Integer pickTimeSeconds;

    @Column(name = "CURRENT_PICK_NO", nullable = false)
    private Integer currentPickNo;

    @Column(name = "CURRENT_DRAFT_TEAM_ID")
    private Long currentDraftTeamId;

    @Column(name = "DEADLINE_AT")
    private LocalDateTime deadlineAt;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

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

    public void update(
            String title,
            String status,
            String orderMode,
            Integer teamCount,
            Integer pickTimeSeconds,
            Integer currentPickNo,
            Long currentDraftTeamId,
            LocalDateTime deadlineAt,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        this.title = title;
        this.status = status;
        this.orderMode = orderMode;
        this.teamCount = teamCount;
        this.pickTimeSeconds = pickTimeSeconds;
        this.currentPickNo = currentPickNo;
        this.currentDraftTeamId = currentDraftTeamId;
        this.deadlineAt = deadlineAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public void advanceTurn(Integer nextPickNo, Long nextDraftTeamId, LocalDateTime nextDeadlineAt) {
        this.currentPickNo = nextPickNo;
        this.currentDraftTeamId = nextDraftTeamId;
        this.deadlineAt = nextDeadlineAt;
    }

    public void start(Long firstDraftTeamId, LocalDateTime startedAt, LocalDateTime deadlineAt) {
        this.status = "LIVE";
        this.currentPickNo = 1;
        this.currentDraftTeamId = firstDraftTeamId;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
        this.endedAt = null;
    }

    public void pause() {
        this.status = "PAUSED";
        this.deadlineAt = null;
    }

    public void resume(LocalDateTime deadlineAt) {
        this.status = "LIVE";
        this.deadlineAt = deadlineAt;
    }

    public void extendDeadlineAt(LocalDateTime deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public void synchronizeCurrentDraftTeam(Long draftTeamId) {
        this.currentDraftTeamId = draftTeamId;
    }

    public void clearCurrentDraftTeam() {
        this.currentDraftTeamId = null;
    }

    public void finish(LocalDateTime endedAt) {
        this.status = "FINISHED";
        this.currentDraftTeamId = null;
        this.deadlineAt = null;
        this.endedAt = endedAt;
    }
}
