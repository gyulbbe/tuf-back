package io.github.gyulbbe.draft.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@IdClass(DraftTeamOperatorId.class)
@Table(
        name = "DRAFT_TEAM_OPERATORS",
        indexes = {
                @Index(name = "idx_draft_team_operators_team", columnList = "DRAFT_TEAM_ID"),
                @Index(name = "idx_draft_team_operators_user", columnList = "OPERATOR_USER_ID")
        }
)
public class DraftTeamOperatorEntity {

    @Id
    @Column(name = "DRAFT_TEAM_ID", nullable = false)
    private Long draftTeamId;

    @Id
    @Column(name = "OPERATOR_USER_ID", nullable = false)
    private Long operatorUserId;

    @Column(name = "ROLE", nullable = false)
    private String role;

    @Column(name = "IS_ACTIVE", nullable = false)
    private String isActive;

    public void update(String role, String isActive) {
        this.role = role;
        this.isActive = isActive;
    }
}
