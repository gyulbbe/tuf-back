package io.github.gyulbbe.rpsdraft.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
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
@SequenceGenerator(
        name = "rps_draft_candidates_seq_gen",
        sequenceName = "RPS_DRAFT_CANDIDATES_SEQ",
        allocationSize = 1
)
@Table(
        name = "RPS_DRAFT_CANDIDATES",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_rps_draft_candidates_session_name", columnNames = {"RPS_DRAFT_SESSION_ID", "CANDIDATE_NAME"}),
                @UniqueConstraint(name = "uk_rps_draft_candidates_session_order", columnNames = {"RPS_DRAFT_SESSION_ID", "DISPLAY_ORDER"}),
                @UniqueConstraint(name = "uk_rps_draft_candidates_id_session", columnNames = {"ID", "RPS_DRAFT_SESSION_ID"})
        },
        indexes = {
                @Index(name = "idx_rps_draft_candidates_session", columnList = "RPS_DRAFT_SESSION_ID"),
                @Index(name = "idx_rps_draft_candidates_session_status", columnList = "RPS_DRAFT_SESSION_ID, STATUS"),
                @Index(name = "idx_rps_draft_candidates_session_order", columnList = "RPS_DRAFT_SESSION_ID, DISPLAY_ORDER")
        }
)
public class RpsDraftCandidateEntity {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_PICKED = "PICKED";
    public static final String STATUS_EXCLUDED = "EXCLUDED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rps_draft_candidates_seq_gen")
    private Long id;

    @Column(name = "RPS_DRAFT_SESSION_ID", nullable = false)
    private Long rpsDraftSessionId;

    @Column(name = "CANDIDATE_NAME", nullable = false, length = 100)
    private String candidateName;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

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
