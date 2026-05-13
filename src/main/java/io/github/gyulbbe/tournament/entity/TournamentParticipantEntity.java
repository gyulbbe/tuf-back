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
        name = "tournament_participants_seq_gen",
        sequenceName = "TOURNAMENT_PARTICIPANTS_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_PARTICIPANTS")
public class TournamentParticipantEntity {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_DROPPED = "DROPPED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_participants_seq_gen")
    private Long id;

    @Column(name = "TOURNAMENT_ID", nullable = false)
    private Long tournamentId;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "PARTICIPANT_NAME")
    private String participantName;

    @Column(name = "SEED_NO")
    private Integer seedNo;

    @Builder.Default
    @Column(name = "STATUS", nullable = false)
    private String status = STATUS_READY;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public boolean isExternalParticipant() {
        return userId == null;
    }

    public void updateSeedNo(Integer seedNo) {
        this.seedNo = seedNo;
    }

    public void updateStatus(String status) {
        this.status = status;
    }

    public void updateParticipantName(String participantName) {
        this.participantName = participantName;
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
