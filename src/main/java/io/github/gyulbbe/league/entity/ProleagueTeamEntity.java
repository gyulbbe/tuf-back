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
        name = "proleague_teams_seq_gen",
        sequenceName = "PROLEAGUE_TEAMS_SEQ",
        allocationSize = 1
)
@Table(name = "PROLEAGUE_TEAMS")
public class ProleagueTeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "proleague_teams_seq_gen")
    private Long id;

    @Column(name = "TEAM_NAME", nullable = false)
    private String teamName;

    @Column(name = "LEAGUE_ID", nullable = false)
    private Long leagueId;

    @Column(name = "TEAM_LEADER_ID")
    private Long leaderId;

    @Column(name = "VICE_LEADER_ID")
    private Long viceLeaderId;

    @Builder.Default
    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder = 1;

    @Column(name = "DRAFT_TEAM_ID")
    private Long draftTeamId;

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
        if (displayOrder == null) {
            displayOrder = 1;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateDate = LocalDateTime.now();
    }

    public void update(String teamName, Long leaderId, Long viceLeaderId, Integer displayOrder, Long draftTeamId) {
        this.teamName = teamName;
        this.leaderId = leaderId;
        this.viceLeaderId = viceLeaderId;
        this.displayOrder = displayOrder;
        this.draftTeamId = draftTeamId;
    }

    public void unlinkDraftTeam() {
        this.draftTeamId = null;
    }
}
