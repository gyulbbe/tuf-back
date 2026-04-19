package io.github.gyulbbe.draft.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@IdClass(DraftPickId.class)
@Table(
        name = "DRAFT_PICKS",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_draft_picks_candidate", columnNames = {"DRAFT_SESSION_ID", "CANDIDATE_USER_ID"})
        },
        indexes = {
                @Index(name = "idx_draft_picks_session", columnList = "DRAFT_SESSION_ID"),
                @Index(name = "idx_draft_picks_team", columnList = "DRAFT_TEAM_ID"),
                @Index(name = "idx_draft_picks_candidate", columnList = "CANDIDATE_USER_ID")
        }
)
public class DraftPickEntity {

    @Id
    @Column(name = "DRAFT_SESSION_ID", nullable = false)
    private Long draftSessionId;

    @Id
    @Column(name = "PICK_NO", nullable = false)
    private Long pickNo;

    @Column(name = "ROUND_NO", nullable = false)
    private Integer roundNo;

    @Column(name = "DRAFT_TEAM_ID", nullable = false)
    private Long draftTeamId;

    @Column(name = "CANDIDATE_USER_ID", nullable = false)
    private Long candidateUserId;

    @Column(name = "PICKED_BY_USER_ID", nullable = false)
    private Long pickedByUserId;

    @Column(name = "PICKED_AT", nullable = false)
    private LocalDateTime pickedAt;

    public void update(Integer roundNo, Long draftTeamId, Long candidateUserId, Long pickedByUserId, LocalDateTime pickedAt) {
        this.roundNo = roundNo;
        this.draftTeamId = draftTeamId;
        this.candidateUserId = candidateUserId;
        this.pickedByUserId = pickedByUserId;
        this.pickedAt = pickedAt;
    }
}
