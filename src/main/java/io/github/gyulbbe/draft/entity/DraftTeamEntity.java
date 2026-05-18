package io.github.gyulbbe.draft.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "draft_teams_seq_gen",
        sequenceName = "DRAFT_TEAMS_SEQ",
        allocationSize = 1
)
@Table(
        name = "DRAFT_TEAMS",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_draft_teams_session_name", columnNames = {"DRAFT_SESSION_ID", "TEAM_NAME"}),
                @UniqueConstraint(name = "uk_draft_teams_session_order", columnNames = {"DRAFT_SESSION_ID", "DISPLAY_ORDER"}),
                @UniqueConstraint(name = "uk_draft_teams_session_proleague_team", columnNames = {"DRAFT_SESSION_ID", "PROLEAGUE_TEAM_ID"})
        },
        indexes = {
                @Index(name = "idx_draft_teams_session", columnList = "DRAFT_SESSION_ID"),
                @Index(name = "idx_draft_teams_proleague_team", columnList = "PROLEAGUE_TEAM_ID")
        }
)
public class DraftTeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "draft_teams_seq_gen")
    private Long id;

    @Column(name = "DRAFT_SESSION_ID", nullable = false)
    private Long draftSessionId;

    @Column(name = "PROLEAGUE_TEAM_ID")
    private Long proleagueTeamId;

    @Column(name = "TEAM_NAME", nullable = false)
    private String teamName;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Column(name = "PICKER_USER_ID")
    private Long pickerUserId;

    public void update(String teamName, Integer displayOrder) {
        this.teamName = teamName;
        this.displayOrder = displayOrder;
    }

    public void assignPicker(Long pickerUserId) {
        this.pickerUserId = pickerUserId;
    }
}
