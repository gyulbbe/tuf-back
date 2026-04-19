package io.github.gyulbbe.league.entity;

import jakarta.persistence.*;
import lombok.*;

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
}
