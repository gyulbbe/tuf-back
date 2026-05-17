package io.github.gyulbbe.home.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "HOME_SCHEDULES")
public class HomeScheduleEntity {

    public static final String LINK_TYPE_DIRECT = "DIRECT";
    public static final String LINK_TYPE_REDIRECT = "REDIRECT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SCHEDULE_GROUP", nullable = false, length = 50)
    private String scheduleGroup;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Column(name = "SCHEDULED_AT", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "TARGET_URL", length = 500)
    private String targetUrl;

    @Builder.Default
    @Column(name = "LINK_TYPE", nullable = false, length = 20)
    private String linkType = LINK_TYPE_DIRECT;

    @Builder.Default
    @Column(name = "DISPLAY_PRIORITY", nullable = false)
    private Integer displayPriority = 0;

    @Column(name = "REG_DATE", nullable = false, updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE", nullable = false)
    private LocalDateTime updateDate;

    @Builder.Default
    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HomeScheduleMatchEntity> matches = new ArrayList<>();

    public void update(
            String scheduleGroup,
            String title,
            String description,
            LocalDateTime scheduledAt,
            String targetUrl,
            String linkType,
            Integer displayPriority
    ) {
        this.scheduleGroup = scheduleGroup;
        this.title = title;
        this.description = description;
        this.scheduledAt = scheduledAt;
        this.targetUrl = targetUrl;
        this.linkType = linkType;
        this.displayPriority = displayPriority;
    }

    public void replaceMatches(List<HomeScheduleMatchEntity> matches) {
        if (this.matches == null) {
            this.matches = new ArrayList<>();
        }
        this.matches.clear();
        if (matches == null) {
            return;
        }
        for (HomeScheduleMatchEntity match : matches) {
            match.attachSchedule(this);
            this.matches.add(match);
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
