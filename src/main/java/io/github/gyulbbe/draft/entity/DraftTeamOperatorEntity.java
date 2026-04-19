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

    @Column(name = "CAN_PICK", nullable = false)
    private String canPick;

    public void update(String role, String isActive) {
        this.role = role;
        this.isActive = isActive;
        if (!isEligiblePicker(role, isActive)) {
            this.canPick = "N";
        }
    }

    public void assignPicker() {
        if (!isEligiblePicker(this.role, this.isActive)) {
            throw new IllegalStateException("활성 팀장/부팀장만 픽 권한을 가질 수 있습니다.");
        }
        this.canPick = "Y";
    }

    public void clearPicker() {
        this.canPick = "N";
    }

    private boolean isEligiblePicker(String role, String isActive) {
        return "Y".equals(isActive) && ("CAPTAIN".equals(role) || "VICE_CAPTAIN".equals(role));
    }
}
