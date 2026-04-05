package io.github.gyulbbe.match.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "MATCH_INFOS")
public class MatchInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "LEAGUE_ID", nullable = false)
    private Long leagueId;

    @Column(name = "MATCH_TYPE")
    private String matchType;

    @Column(name = "FORMAT")
    private String format;

    @Column(name = "WINNER")
    private String winner;

    @Column(name = "LOSER")
    private String loser;

    @Column(name = "SETS")
    private String sets;
}
