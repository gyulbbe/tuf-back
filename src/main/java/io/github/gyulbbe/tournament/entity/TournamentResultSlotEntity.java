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
        name = "tournament_result_slots_seq_gen",
        sequenceName = "TOURNAMENT_RESULT_SLOTS_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_RESULT_SLOTS")
public class TournamentResultSlotEntity {

    public static final String TYPE_QUALIFIED = "QUALIFIED";
    public static final String TYPE_CHAMPION = "CHAMPION";
    public static final String TYPE_RUNNER_UP = "RUNNER_UP";
    public static final String TYPE_ELIMINATED = "ELIMINATED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_result_slots_seq_gen")
    private Long id;

    @Column(name = "STAGE_ID", nullable = false)
    private Long stageId;

    @Column(name = "GROUP_ID")
    private Long groupId;

    @Column(name = "RESULT_KEY", nullable = false)
    private String resultKey;

    @Column(name = "RESULT_TYPE", nullable = false)
    private String resultType;

    @Column(name = "RANK_NO")
    private Integer rankNo;

    @Column(name = "LABEL", nullable = false)
    private String label;

    @Column(name = "PARTICIPANT_ID")
    private Long participantId;

    @Column(name = "DECIDED_AT")
    private LocalDateTime decidedAt;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public void decide(Long participantId, LocalDateTime decidedAt) {
        this.participantId = participantId;
        this.decidedAt = decidedAt;
    }

    public void clearDecision() {
        this.participantId = null;
        this.decidedAt = null;
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
