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
        name = "tournament_match_slots_seq_gen",
        sequenceName = "TOURNAMENT_MATCH_SLOTS_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_MATCH_SLOTS")
public class TournamentMatchSlotEntity {

    public static final String OUTCOME_WINNER = "WINNER";
    public static final String OUTCOME_LOSER = "LOSER";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_match_slots_seq_gen")
    private Long id;

    @Column(name = "MATCH_ID", nullable = false)
    private Long matchId;

    @Column(name = "SLOT_NO", nullable = false)
    private Integer slotNo;

    @Column(name = "PARTICIPANT_ID")
    private Long participantId;

    @Column(name = "SOURCE_MATCH_ID")
    private Long sourceMatchId;

    @Column(name = "SOURCE_OUTCOME")
    private String sourceOutcome;

    @Column(name = "PLACEHOLDER_LABEL")
    private String placeholderLabel;

    @Column(name = "SCORE")
    private Integer score;

    @Builder.Default
    @Column(name = "IS_WINNER", nullable = false)
    private Integer isWinner = 0;

    @Builder.Default
    @Column(name = "IS_BYE", nullable = false)
    private Integer isBye = 0;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public void assignParticipant(Long participantId) {
        this.participantId = participantId;
        this.sourceMatchId = null;
        this.sourceOutcome = null;
        this.placeholderLabel = null;
        this.score = null;
        this.isWinner = 0;
        this.isBye = 0;
    }

    public void clearParticipant(String placeholderLabel) {
        this.participantId = null;
        this.sourceMatchId = null;
        this.sourceOutcome = null;
        this.placeholderLabel = placeholderLabel;
        this.score = null;
        this.isWinner = 0;
        this.isBye = 0;
    }

    public void clearParticipant() {
        clearParticipant(null);
    }

    public void updateScore(Integer score) {
        this.score = score;
    }

    public void markWinner(boolean winner) {
        this.isWinner = winner ? 1 : 0;
    }

    public void markBye(boolean bye) {
        this.isBye = bye ? 1 : 0;
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
