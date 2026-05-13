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
        name = "tournament_routes_seq_gen",
        sequenceName = "TOURNAMENT_ROUTES_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_ROUTES")
public class TournamentRouteEntity {

    public static final String OUTCOME_WINNER = "WINNER";
    public static final String OUTCOME_LOSER = "LOSER";
    public static final String TARGET_MATCH_SLOT = "MATCH_SLOT";
    public static final String TARGET_RESULT_SLOT = "RESULT_SLOT";
    public static final String TARGET_ELIMINATED = "ELIMINATED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_routes_seq_gen")
    private Long id;

    @Column(name = "FROM_MATCH_ID", nullable = false)
    private Long fromMatchId;

    @Column(name = "OUTCOME", nullable = false)
    private String outcome;

    @Column(name = "TARGET_TYPE", nullable = false)
    private String targetType;

    @Column(name = "TO_MATCH_ID")
    private Long toMatchId;

    @Column(name = "TO_SLOT_NO")
    private Integer toSlotNo;

    @Column(name = "TO_RESULT_SLOT_ID")
    private Long toResultSlotId;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public boolean isMatchSlotTarget() {
        return TARGET_MATCH_SLOT.equals(targetType);
    }

    public boolean isResultSlotTarget() {
        return TARGET_RESULT_SLOT.equals(targetType);
    }

    public boolean isEliminatedTarget() {
        return TARGET_ELIMINATED.equals(targetType);
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
