package io.github.gyulbbe.entrysubmission.entity;

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
        name = "entry_submission_teams_seq_gen",
        sequenceName = "ENTRY_SUBMISSION_TEAMS_SEQ",
        allocationSize = 1
)
@Table(
        name = "ENTRY_SUBMISSION_TEAMS",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_entry_submission_teams_session_order", columnNames = {"ENTRY_SUBMISSION_SESSION_ID", "DISPLAY_ORDER"}),
                @UniqueConstraint(name = "uq_entry_submission_teams_session_captain", columnNames = {"ENTRY_SUBMISSION_SESSION_ID", "CAPTAIN_USER_ID"}),
                @UniqueConstraint(name = "uq_entry_submission_teams_id_session", columnNames = {"ID", "ENTRY_SUBMISSION_SESSION_ID"})
        },
        indexes = {
                @Index(name = "idx_entry_submission_teams_session", columnList = "ENTRY_SUBMISSION_SESSION_ID"),
                @Index(name = "idx_entry_submission_teams_captain", columnList = "CAPTAIN_USER_ID")
        }
)
public class EntrySubmissionTeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entry_submission_teams_seq_gen")
    private Long id;

    @Column(name = "ENTRY_SUBMISSION_SESSION_ID", nullable = false)
    private Long entrySubmissionSessionId;

    @Column(name = "TEAM_NAME", nullable = false)
    private String teamName;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Column(name = "CAPTAIN_USER_ID", nullable = false)
    private Long captainUserId;

    @Column(name = "SUBMITTED_AT")
    private LocalDateTime submittedAt;

    public void markSubmitted(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}
