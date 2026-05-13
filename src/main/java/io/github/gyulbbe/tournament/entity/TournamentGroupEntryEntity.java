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
        name = "tournament_group_entries_seq_gen",
        sequenceName = "TOURNAMENT_GROUP_ENTRIES_SEQ",
        allocationSize = 1
)
@Table(name = "TOURNAMENT_GROUP_ENTRIES")
public class TournamentGroupEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tournament_group_entries_seq_gen")
    private Long id;

    @Column(name = "GROUP_ID", nullable = false)
    private Long groupId;

    @Column(name = "PARTICIPANT_ID", nullable = false)
    private Long participantId;

    @Column(name = "GROUP_SEED_NO", nullable = false)
    private Integer groupSeedNo;

    @Column(name = "ENTRY_LABEL")
    private String entryLabel;

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
