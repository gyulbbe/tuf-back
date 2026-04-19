package io.github.gyulbbe.draft.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@IdClass(DraftOrderId.class)
@Table(
        name = "DRAFT_ORDERS",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_draft_orders_round_team", columnNames = {"DRAFT_SESSION_ID", "ROUND_NO", "DRAFT_TEAM_ID"})
        },
        indexes = {
                @Index(name = "idx_draft_orders_session", columnList = "DRAFT_SESSION_ID"),
                @Index(name = "idx_draft_orders_team", columnList = "DRAFT_TEAM_ID")
        }
)
public class DraftOrderEntity {

    @Id
    @Column(name = "DRAFT_SESSION_ID", nullable = false)
    private Long draftSessionId;

    @Id
    @Column(name = "PICK_NO", nullable = false)
    private Long pickNo;

    @Column(name = "ROUND_NO", nullable = false)
    private Integer roundNo;

    @Column(name = "DRAFT_TEAM_ID", nullable = false)
    private Long draftTeamId;

    public void update(Integer roundNo, Long draftTeamId) {
        this.roundNo = roundNo;
        this.draftTeamId = draftTeamId;
    }
}
