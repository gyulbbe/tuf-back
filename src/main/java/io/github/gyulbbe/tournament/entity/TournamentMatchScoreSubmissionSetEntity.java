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
        name = "tournament_match_score_submission_sets_seq_gen",
        sequenceName = "TOURNAMENT_MATCH_SCORE_SUB_SETS_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_MATCH_SCORE_SUBMISSION_SETS")
public class TournamentMatchScoreSubmissionSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_match_score_submission_sets_seq_gen")
    private Long id;

    @Column(name = "SCORE_SUBMISSION_ID", nullable = false)
    private Long scoreSubmissionId;

    @Column(name = "SET_NO", nullable = false)
    private Integer setNo;

    @Column(name = "WINNER_SLOT_NO", nullable = false)
    private Integer winnerSlotNo;

    @Column(name = "MAP_ID", nullable = false)
    private Long mapId;

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
