package io.github.gyulbbe.match.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "match_infos_seq_gen",
        sequenceName = "MATCH_INFOS_SEQ",
        allocationSize = 1
)
@Table(name = "MATCH_INFOS")
public class MatchInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "match_infos_seq_gen")
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
