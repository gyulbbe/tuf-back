package io.github.gyulbbe.tournament.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
        name = "race_survival_progress_submissions_seq_gen",
        sequenceName = "RACE_SURVIVAL_PROGRESS_SUBMISSIONS_SEQ",
        allocationSize = 1
)
@Table(name = "RACE_SURVIVAL_PROGRESS_SUBMISSIONS")
public class RaceSurvivalProgressSubmissionEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "race_survival_progress_submissions_seq_gen")
    private Long id;

    @Column(name = "TOURNAMENT_ID", nullable = false)
    private Long tournamentId;

    @Column(name = "SUBMITTED_BY_USER_ID", nullable = false)
    private Long submittedByUserId;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "REVIEWED_BY_USER_ID")
    private Long reviewedByUserId;

    @Column(name = "ADMIN_NOTE")
    private String adminNote;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public void approve(Long reviewedByUserId, LocalDateTime reviewedAt) {
        this.status = STATUS_APPROVED;
        this.reviewedByUserId = reviewedByUserId;
        this.reviewedAt = reviewedAt;
        this.adminNote = null;
    }

    public void reject(Long reviewedByUserId, LocalDateTime reviewedAt, String adminNote) {
        this.status = STATUS_REJECTED;
        this.reviewedByUserId = reviewedByUserId;
        this.reviewedAt = reviewedAt;
        this.adminNote = adminNote;
    }

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
}
