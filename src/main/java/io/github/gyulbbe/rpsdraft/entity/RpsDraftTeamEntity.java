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

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "rps_draft_teams_seq_gen",
        sequenceName = "RPS_DRAFT_TEAMS_SEQ",
        allocationSize = 1
)
@Table(
        name = "RPS_DRAFT_TEAMS",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_rps_draft_teams_session_name", columnNames = {"RPS_DRAFT_SESSION_ID", "TEAM_NAME"}),
                @UniqueConstraint(name = "uq_rps_draft_teams_session_order", columnNames = {"RPS_DRAFT_SESSION_ID", "DISPLAY_ORDER"}),
                @UniqueConstraint(name = "uq_rps_draft_teams_session_picker", columnNames = {"RPS_DRAFT_SESSION_ID", "PICKER_USER_ID"}),
                @UniqueConstraint(name = "uq_rps_draft_teams_id_session", columnNames = {"ID", "RPS_DRAFT_SESSION_ID"})
        },
        indexes = {
                @Index(name = "idx_rps_draft_teams_session", columnList = "RPS_DRAFT_SESSION_ID"),
                @Index(name = "idx_rps_draft_teams_picker", columnList = "PICKER_USER_ID")
        }
)
public class RpsDraftTeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rps_draft_teams_seq_gen")
    private Long id;

    @Column(name = "RPS_DRAFT_SESSION_ID", nullable = false)
    private Long rpsDraftSessionId;

    @Column(name = "TEAM_NAME", nullable = false)
    private String teamName;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Column(name = "PICKER_USER_ID")
    private Long pickerUserId;
}
