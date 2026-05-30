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

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "entry_submission_players_seq_gen",
        sequenceName = "ENTRY_SUBMISSION_PLAYERS_SEQ",
        allocationSize = 1
)
@Table(
        name = "ENTRY_SUBMISSION_PLAYERS",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_entry_submission_players_team_name", columnNames = {"ENTRY_SUBMISSION_TEAM_ID", "PLAYER_NAME"}),
                @UniqueConstraint(name = "uq_entry_submission_players_team_order", columnNames = {"ENTRY_SUBMISSION_TEAM_ID", "DISPLAY_ORDER"}),
                @UniqueConstraint(name = "uq_entry_submission_players_id_session", columnNames = {"ID", "ENTRY_SUBMISSION_SESSION_ID"})
        },
        indexes = {
                @Index(name = "idx_entry_submission_players_session", columnList = "ENTRY_SUBMISSION_SESSION_ID"),
                @Index(name = "idx_entry_submission_players_team", columnList = "ENTRY_SUBMISSION_TEAM_ID")
        }
)
public class EntrySubmissionPlayerEntity {

    public static final String CAPTAIN_Y = "Y";
    public static final String CAPTAIN_N = "N";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entry_submission_players_seq_gen")
    private Long id;

    @Column(name = "ENTRY_SUBMISSION_SESSION_ID", nullable = false)
    private Long entrySubmissionSessionId;

    @Column(name = "ENTRY_SUBMISSION_TEAM_ID", nullable = false)
    private Long entrySubmissionTeamId;

    @Column(name = "PLAYER_NAME", nullable = false, length = 100)
    private String playerName;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Builder.Default
    @Column(name = "CAPTAIN_YN", nullable = false, length = 1)
    private String captainYn = CAPTAIN_N;
}
