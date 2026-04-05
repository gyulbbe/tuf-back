package io.github.gyulbbe.league.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "LEAGUE_PARTICIPATIONS")
public class LeagueParticipationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
