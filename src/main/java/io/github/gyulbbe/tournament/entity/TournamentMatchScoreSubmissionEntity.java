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
        name = "tournament_match_score_submissions_seq_gen",
        sequenceName = "TOURNAMENT_MATCH_SCORE_SUBMISSIONS_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_MATCH_SCORE_SUBMISSIONS")
public class TournamentMatchScoreSubmissionEntity {

    public static final String ROLE_PLAYER = "PLAYER";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_match_score_submissions_seq_gen")
    private Long id;

    @Column(name = "TOURNAMENT_ID", nullable = false)
    private Long tournamentId;

    @Column(name = "MATCH_ID", nullable = false)
    private Long matchId;

    @Column(name = "SUBMITTED_BY_USER_ID", nullable = false)
    private Long submittedByUserId;

    @Column(name = "SUBMITTED_BY_PARTICIPANT_ID")
    private Long submittedByParticipantId;

    @Column(name = "SUBMITTER_ROLE", nullable = false)
    private String submitterRole;

    @Column(name = "SLOT1_SCORE", nullable = false)
    private Integer slot1Score;

    @Column(name = "SLOT2_SCORE", nullable = false)
    private Integer slot2Score;

    @Column(name = "WINNER_SLOT_NO", nullable = false)
    private Integer winnerSlotNo;

    @Column(name = "MAP_ID")
    private Long mapId;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "ADMIN_REVIEWER_USER_ID")
    private Long adminReviewerUserId;

    @Column(name = "ADMIN_REVIEWED_AT")
    private LocalDateTime adminReviewedAt;

    @Column(name = "ADMIN_NOTE")
    private String adminNote;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public void approve(Long adminReviewerUserId, LocalDateTime reviewedAt, String adminNote) {
        this.status = STATUS_APPROVED;
        this.adminReviewerUserId = adminReviewerUserId;
        this.adminReviewedAt = reviewedAt;
        this.adminNote = adminNote;
    }

    public void reject(Long adminReviewerUserId, LocalDateTime reviewedAt, String adminNote) {
        this.status = STATUS_REJECTED;
        this.adminReviewerUserId = adminReviewerUserId;
        this.adminReviewedAt = reviewedAt;
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
