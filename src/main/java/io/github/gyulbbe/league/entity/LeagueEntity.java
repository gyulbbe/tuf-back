package io.github.gyulbbe.league.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    public static final String STATUS_READY = "READY";
    public static final String STATUS_LIVE = "LIVE";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String TYPE_PROLEAGUE = "PROLEAGUE";
    public static final String TYPE_PERSONAL = "PERSONAL";
    public static final String TYPE_ULTIMATE_BATTLE = "ULTIMATE_BATTLE";
    public static final String TYPE_RACE_SURVIVAL = "RACE_SURVIVAL";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leagues_seq_gen")
    private Long id;

    @Column(name = "LEAGUE_NAME", nullable = false)
    private String leagueName;

    @Column(name = "SEASON_NAME")
    private String seasonName;

    @Column(name = "DESCRIPTION")
    private String description;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_READY;

    @Column(name = "LEAGUE_TYPE", nullable = false)
    private String leagueType;

    @Column(name = "START_DATE")
    private LocalDate startDate;

    @Column(name = "END_DATE")
    private LocalDate endDate;

    @Column(name = "DRAFT_SESSION_ID")
    private Long draftSessionId;

    @Column(name = "TOURNAMENT_ID")
    private Long tournamentId;

    @Column(name = "CHAMPION_TEAM_ID")
    private Long championTeamId;

    @Column(name = "RUNNER_UP_TEAM_ID")
    private Long runnerUpTeamId;

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
            status = STATUS_READY;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateDate = LocalDateTime.now();
    }

    public void updateBasic(
            String leagueName,
            String seasonName,
            String description,
            String status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.leagueName = leagueName;
        this.seasonName = seasonName;
        this.description = description;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void linkDraftSession(Long draftSessionId) {
        this.draftSessionId = draftSessionId;
    }

    public void linkTournament(Long tournamentId) {
        this.tournamentId = tournamentId;
    }

    public void unlinkDraftSession() {
        this.draftSessionId = null;
    }

    public void clearResultTeams() {
        this.championTeamId = null;
        this.runnerUpTeamId = null;
    }

    public void finish(Long championTeamId, Long runnerUpTeamId, LocalDate endDate) {
        this.status = STATUS_FINISHED;
        this.championTeamId = championTeamId;
        this.runnerUpTeamId = runnerUpTeamId;
        this.endDate = endDate;
    }
}
