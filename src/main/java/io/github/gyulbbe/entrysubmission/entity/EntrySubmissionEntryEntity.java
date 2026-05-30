package io.github.gyulbbe.entrysubmission.entity;

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
@IdClass(EntrySubmissionEntryId.class)
@Table(
        name = "ENTRY_SUBMISSION_ENTRIES",
        indexes = {
                @Index(name = "idx_entry_submission_entries_session", columnList = "ENTRY_SUBMISSION_SESSION_ID"),
                @Index(name = "idx_entry_submission_entries_team", columnList = "ENTRY_SUBMISSION_TEAM_ID"),
                @Index(name = "idx_entry_submission_entries_player", columnList = "ENTRY_SUBMISSION_PLAYER_ID")
        }
)
public class EntrySubmissionEntryEntity {

    @Id
    @Column(name = "ENTRY_SUBMISSION_SESSION_ID", nullable = false)
    private Long entrySubmissionSessionId;

    @Id
    @Column(name = "ENTRY_SUBMISSION_TEAM_ID", nullable = false)
    private Long entrySubmissionTeamId;

    @Id
    @Column(name = "SET_NO", nullable = false)
    private Integer setNo;

    @Column(name = "ENTRY_SUBMISSION_PLAYER_ID", nullable = false)
    private Long entrySubmissionPlayerId;

    @Column(name = "SUBMITTED_BY_USER_ID", nullable = false)
    private Long submittedByUserId;

    @Column(name = "SUBMITTED_AT", nullable = false)
    private LocalDateTime submittedAt;
}
