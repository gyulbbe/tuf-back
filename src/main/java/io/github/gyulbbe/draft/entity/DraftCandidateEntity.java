package io.github.gyulbbe.draft.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@IdClass(DraftCandidateId.class)
@Table(
        name = "DRAFT_CANDIDATES",
        indexes = {
                @Index(name = "idx_draft_candidates_session", columnList = "DRAFT_SESSION_ID"),
                @Index(name = "idx_draft_candidates_user", columnList = "CANDIDATE_USER_ID"),
                @Index(name = "idx_draft_candidates_session_status", columnList = "DRAFT_SESSION_ID, STATUS")
        }
)
public class DraftCandidateEntity {

    @Id
    @Column(name = "DRAFT_SESSION_ID", nullable = false)
    private Long draftSessionId;

    @Id
    @Column(name = "CANDIDATE_USER_ID", nullable = false)
    private Long candidateUserId;

    @Column(name = "CANDIDATE_NAME", nullable = false)
    private String candidateName;

    @Column(name = "RACE", nullable = false)
    private String race;

    @Column(name = "STATUS", nullable = false)
    private String status;

    @Column(name = "PICKED_DRAFT_TEAM_ID")
    private Long pickedDraftTeamId;

    @Column(name = "PICKED_AT")
    private LocalDateTime pickedAt;

    public void update(String candidateName, String race, String status, Long pickedDraftTeamId, LocalDateTime pickedAt) {
        this.candidateName = candidateName;
        this.race = race;
        this.status = status;
        this.pickedDraftTeamId = pickedDraftTeamId;
        this.pickedAt = pickedAt;
    }

    public void markPicked(Long draftTeamId, LocalDateTime pickedAt) {
        this.status = "PICKED";
        this.pickedDraftTeamId = draftTeamId;
        this.pickedAt = pickedAt;
    }

    public void resetToWaiting() {
        this.status = "WAITING";
        this.pickedDraftTeamId = null;
        this.pickedAt = null;
    }
}
