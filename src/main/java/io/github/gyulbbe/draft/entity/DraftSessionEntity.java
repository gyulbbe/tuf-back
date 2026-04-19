package io.github.gyulbbe.draft.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "DRAFT_SESSIONS",
        indexes = {
                @Index(name = "idx_draft_sessions_status", columnList = "STATUS")
        }
)
public class DraftSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "STATUS", nullable = false)
    private String status;

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

    public void update(
            String title,
            String status,
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

    public void finish(LocalDateTime endedAt) {
        this.status = "FINISHED";
        this.currentDraftTeamId = null;
        this.deadlineAt = null;
        this.endedAt = endedAt;
    }
}
