package io.github.gyulbbe.rpsdraft.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
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
@IdClass(RpsDraftCandidateId.class)
@Table(
        name = "RPS_DRAFT_CANDIDATES",
        indexes = {
                @Index(name = "idx_rps_draft_candidates_session", columnList = "RPS_DRAFT_SESSION_ID"),
                @Index(name = "idx_rps_draft_candidates_user", columnList = "CANDIDATE_USER_ID"),
                @Index(name = "idx_rps_draft_candidates_session_status", columnList = "RPS_DRAFT_SESSION_ID, STATUS")
        }
)
public class RpsDraftCandidateEntity {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_PICKED = "PICKED";
    public static final String STATUS_EXCLUDED = "EXCLUDED";

    @Id
    @Column(name = "RPS_DRAFT_SESSION_ID", nullable = false)
    private Long rpsDraftSessionId;

    @Id
    @Column(name = "CANDIDATE_USER_ID", nullable = false)
    private Long candidateUserId;

    @Column(name = "CANDIDATE_NAME", nullable = false)
    private String candidateName;

    @Column(name = "RACE")
    private String race;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_WAITING;

    @Column(name = "PICKED_RPS_DRAFT_TEAM_ID")
    private Long pickedRpsDraftTeamId;

    @Column(name = "PICKED_AT")
    private LocalDateTime pickedAt;

    public void markPicked(Long rpsDraftTeamId, LocalDateTime pickedAt) {
        this.status = STATUS_PICKED;
        this.pickedRpsDraftTeamId = rpsDraftTeamId;
        this.pickedAt = pickedAt;
    }
}
