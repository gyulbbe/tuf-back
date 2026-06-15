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
        name = "tournament_match_sets_seq_gen",
        sequenceName = "TOURNAMENT_MATCH_SETS_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_MATCH_SETS")
public class TournamentMatchSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_match_sets_seq_gen")
    private Long id;

    @Column(name = "MATCH_ID", nullable = false)
    private Long matchId;

    @Column(name = "SET_NO", nullable = false)
    private Integer setNo;

    @Column(name = "MAP_ID")
    private Long mapId;

    @Column(name = "WINNER_SLOT_NO")
    private Integer winnerSlotNo;

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
