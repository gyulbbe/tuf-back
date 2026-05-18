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
        name = "tournaments_seq_gen",
        sequenceName = "TOURNAMENTS_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENTS")
public class TournamentEntity {

    public static final String STATUS_LIVE = "LIVE";
    public static final String STATUS_FINISHED = "FINISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournaments_seq_gen")
    private Long id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "OWNER_USER_ID")
    private Long ownerUserId;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_LIVE;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public void updateTitle(String title) {
        this.title = title;
    }

    public void finish() {
        this.status = STATUS_FINISHED;
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
