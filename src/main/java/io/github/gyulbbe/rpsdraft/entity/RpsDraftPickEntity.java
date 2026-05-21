package io.github.gyulbbe.rpsdraft.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@IdClass(RpsDraftPickId.class)
@Table(
        name = "RPS_DRAFT_PICKS",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_rps_draft_picks_session_candidate", columnNames = {"RPS_DRAFT_SESSION_ID", "CANDIDATE_ID"})
        },
        indexes = {
                @Index(name = "idx_rps_draft_picks_session", columnList = "RPS_DRAFT_SESSION_ID"),
                @Index(name = "idx_rps_draft_picks_team", columnList = "RPS_DRAFT_TEAM_ID"),
                @Index(name = "idx_rps_draft_picks_candidate", columnList = "CANDIDATE_ID")
        }
)
public class RpsDraftPickEntity {

    @Id
    @Column(name = "RPS_DRAFT_SESSION_ID", nullable = false)
    private Long rpsDraftSessionId;

    @Id
    @Column(name = "PICK_NO", nullable = false)
    private Long pickNo;

    @Column(name = "RPS_DRAFT_TEAM_ID", nullable = false)
    private Long rpsDraftTeamId;

    @Column(name = "CANDIDATE_ID", nullable = false)
    private Long candidateId;

    @Column(name = "PICKED_BY_USER_ID", nullable = false)
    private Long pickedByUserId;

    @Column(name = "PICKED_AT", nullable = false)
    private LocalDateTime pickedAt;
}
