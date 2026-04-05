package io.github.gyulbbe.league.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "PROLEAGUE_TEAMS")
public class ProleagueTeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TEAM_NAME", nullable = false)
    private String teamName;

    @Column(name = "LEAGUE_ID", nullable = false)
    private Long leagueId;

    @Column(name = "LEADER_ID")
    private Long leaderId;

    @Column(name = "VICE_LEADER_ID")
    private Long viceLeaderId;
}
