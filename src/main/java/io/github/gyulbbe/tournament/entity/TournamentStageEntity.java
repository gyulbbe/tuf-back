package io.github.gyulbbe.tournament.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
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
        name = "tournament_stages_seq_gen",
        sequenceName = "TOURNAMENT_STAGES_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_STAGES")
public class TournamentStageEntity {

    public static final String TYPE_DUAL_GROUP = "DUAL_GROUP";
    public static final String TYPE_SINGLE_ELIMINATION = "SINGLE_ELIMINATION";
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FINISHED = "FINISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_stages_seq_gen")
    private Long id;

    @Column(name = "TOURNAMENT_ID", nullable = false)
    private Long tournamentId;

    @Column(name = "STAGE_NO", nullable = false)
    private Integer stageNo;

    @Column(name = "STAGE_NAME", nullable = false)
    private String stageName;

    @Column(name = "STAGE_TYPE", nullable = false)
    private String stageType;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_DRAFT;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (regDate == null) {
            regDate = now;
        }
        if (updateDate == null) {
            updateDate = regDate;
        }
    }

    @PreUpdate
    void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
