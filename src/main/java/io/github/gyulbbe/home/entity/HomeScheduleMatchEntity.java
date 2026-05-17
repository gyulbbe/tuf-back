package io.github.gyulbbe.home.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "home_schedule_matches_seq_gen",
        sequenceName = "HOME_SCHEDULE_MATCHES_SEQ",
        allocationSize = 1
)
@Table(name = "HOME_SCHEDULE_MATCHES")
public class HomeScheduleMatchEntity {

    public static final String FORMAT_1V1 = "1V1";
    public static final String FORMAT_2V2 = "2V2";
    public static final String FORMAT_3V3 = "3V3";
    public static final String FORMAT_ACE = "ACE";
    public static final String FORMAT_CUSTOM = "CUSTOM";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "home_schedule_matches_seq_gen")
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SCHEDULE_ID", nullable = false)
    private HomeScheduleEntity schedule;

    @Column(name = "SCHEDULE_ID", insertable = false, updatable = false)
    private Long scheduleId;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Column(name = "SET_LABEL", nullable = false, length = 50)
    private String setLabel;

    @Builder.Default
    @Column(name = "MATCH_FORMAT", nullable = false, length = 20)
    private String matchFormat = FORMAT_1V1;

    @Column(name = "TEAM_A_NAME", length = 100)
    private String teamAName;

    @Column(name = "TEAM_B_NAME", length = 100)
    private String teamBName;

    @Column(name = "MAP_ID")
    private Long mapId;

    @Column(name = "NOTE", length = 300)
    private String note;

    @Column(name = "REG_DATE", nullable = false, updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE", nullable = false)
    private LocalDateTime updateDate;

    @Builder.Default
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HomeScheduleMatchPlayerEntity> players = new ArrayList<>();

    public void attachSchedule(HomeScheduleEntity schedule) {
        this.schedule = schedule;
    }

    public void replacePlayers(List<HomeScheduleMatchPlayerEntity> players) {
        if (this.players == null) {
            this.players = new ArrayList<>();
        }
        this.players.clear();
        if (players == null) {
            return;
        }
        for (HomeScheduleMatchPlayerEntity player : players) {
            player.attachMatch(this);
            this.players.add(player);
        }
    }

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
