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
        name = "race_survival_progress_submission_matches_seq_gen",
        sequenceName = "RACE_SURVIVAL_PROGRESS_SUB_MATCHES_SEQ",
        allocationSize = 1
)
@Table(name = "RACE_SURVIVAL_PROGRESS_SUBMISSION_MATCHES")
public class RaceSurvivalProgressSubmissionMatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "race_survival_progress_submission_matches_seq_gen")
    private Long id;

    @Column(name = "SUBMISSION_ID", nullable = false)
    private Long submissionId;

    @Column(name = "MATCH_ORDER", nullable = false)
    private Integer matchOrder;

    @Column(name = "MAP_ID")
    private Long mapId;

    @Column(name = "SLOT1_PARTICIPANT_ID", nullable = false)
    private Long slot1ParticipantId;

    @Column(name = "SLOT2_PARTICIPANT_ID", nullable = false)
    private Long slot2ParticipantId;

    @Column(name = "SLOT1_SCORE", nullable = false)
    private Integer slot1Score;

    @Column(name = "SLOT2_SCORE", nullable = false)
    private Integer slot2Score;

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
}
