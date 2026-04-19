package io.github.gyulbbe.draft.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "DRAFT_TEAMS",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_draft_teams_session_name", columnNames = {"DRAFT_SESSION_ID", "TEAM_NAME"}),
                @UniqueConstraint(name = "uk_draft_teams_session_order", columnNames = {"DRAFT_SESSION_ID", "DISPLAY_ORDER"})
        },
        indexes = {
                @Index(name = "idx_draft_teams_session", columnList = "DRAFT_SESSION_ID")
        }
)
public class DraftTeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "DRAFT_SESSION_ID", nullable = false)
    private Long draftSessionId;

    @Column(name = "TEAM_NAME", nullable = false)
    private String teamName;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    public void update(String teamName, Integer displayOrder) {
        this.teamName = teamName;
        this.displayOrder = displayOrder;
    }
}
