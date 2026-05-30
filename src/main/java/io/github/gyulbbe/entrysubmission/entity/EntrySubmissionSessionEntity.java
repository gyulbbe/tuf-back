package io.github.gyulbbe.entrysubmission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
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
@SequenceGenerator(
        name = "entry_submission_sessions_seq_gen",
        sequenceName = "ENTRY_SUBMISSION_SESSIONS_SEQ",
        allocationSize = 1
)
@Table(
        name = "ENTRY_SUBMISSION_SESSIONS",
        indexes = {
                @Index(name = "idx_entry_submission_sessions_status", columnList = "STATUS"),
                @Index(name = "idx_entry_submission_sessions_owner", columnList = "OWNER_USER_ID")
        }
)
public class EntrySubmissionSessionEntity {

    public static final String STATUS_SUBMITTING = "SUBMITTING";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entry_submission_sessions_seq_gen")
    private Long id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "OWNER_USER_ID", nullable = false)
    private Long ownerUserId;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_SUBMITTING;

    @Column(name = "SET_COUNT", nullable = false)
    private Integer setCount;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (regDate == null) {
            regDate = now;
        }
        if (updateDate == null) {
            updateDate = regDate;
        }
    }

    @PreUpdate
    void onUpdate() {
        updateDate = LocalDateTime.now();
    }

    public void complete(LocalDateTime completedAt) {
        this.status = STATUS_COMPLETED;
        this.completedAt = completedAt;
    }

    public void restart() {
        this.status = STATUS_SUBMITTING;
        this.completedAt = null;
    }
}
