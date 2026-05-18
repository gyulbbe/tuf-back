package io.github.gyulbbe.league.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "proleague_team_members_seq_gen",
        sequenceName = "PROLEAGUE_TEAM_MEMBERS_SEQ",
        allocationSize = 1
)
@Table(
        name = "PROLEAGUE_TEAM_MEMBERS",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_proleague_team_members_league_user_status",
                        columnNames = {"LEAGUE_ID", "USER_ID", "STATUS"}
                )
        },
        indexes = {
                @Index(name = "idx_proleague_team_members_league", columnList = "LEAGUE_ID, STATUS"),
                @Index(name = "idx_proleague_team_members_team", columnList = "PROLEAGUE_TEAM_ID"),
                @Index(name = "idx_proleague_team_members_user", columnList = "USER_ID"),
                @Index(name = "idx_proleague_team_members_source_draft", columnList = "SOURCE_DRAFT_SESSION_ID")
        }
)
public class ProleagueTeamMemberEntity {

    public static final String SOURCE_DRAFT = "DRAFT";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REMOVED = "REMOVED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "proleague_team_members_seq_gen")
    private Long id;

    @Column(name = "LEAGUE_ID", nullable = false)
    private Long leagueId;

    @Column(name = "PROLEAGUE_TEAM_ID", nullable = false)
    private Long proleagueTeamId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Builder.Default
    @Column(name = "SOURCE", nullable = false)
    private String source = SOURCE_DRAFT;

    @Column(name = "SOURCE_DRAFT_SESSION_ID")
    private Long sourceDraftSessionId;

    @Column(name = "DRAFT_PICK_NO")
    private Long draftPickNo;

    @Builder.Default
    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder = 1;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_ACTIVE;

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
        if (source == null || source.isBlank()) {
            source = SOURCE_DRAFT;
        }
        if (status == null || status.isBlank()) {
            status = STATUS_ACTIVE;
        }
        if (displayOrder == null || displayOrder <= 0) {
            displayOrder = 1;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateDate = LocalDateTime.now();
    }
}
