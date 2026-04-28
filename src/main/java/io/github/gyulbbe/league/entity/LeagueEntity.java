package io.github.gyulbbe.league.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "leagues_seq_gen",
        sequenceName = "LEAGUES_SEQ",
        allocationSize = 1
)
@Table(name = "LEAGUES")
public class LeagueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leagues_seq_gen")
    private Long id;

    @Column(name = "LEAGUE_NAME", nullable = false)
    private String leagueName;

    @Column(name = "START_DATE")
    private LocalDate startDate;

    @Column(name = "END_DATE")
    private LocalDate endDate;
}
