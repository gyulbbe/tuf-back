package io.github.gyulbbe.league.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "league_participations_seq_gen",
        sequenceName = "LEAGUE_PARTICIPATIONS_SEQ",
        allocationSize = 1
)
@Table(name = "LEAGUE_PARTICIPATIONS")
public class LeagueParticipationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "league_participations_seq_gen")
    private Long id;

    @Column(name = "LEAGUE_ID", nullable = false)
    private Long leagueId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "RACE")
    private String race;

    @Builder.Default
    @Column(name = "STATUS")
    private String status = "ACTIVE";

    @Column(name = "REG_DATE")
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    public void prePersist() {
        if (regDate == null) {
            regDate = LocalDateTime.now();
        }
        if (updateDate == null) {
            updateDate = regDate;
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateDate = LocalDateTime.now();
    }
}
