package io.github.gyulbbe.board.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
        name = "boards_seq_gen",
        sequenceName = "BOARDS_SEQ",
        allocationSize = 1
)
@Table(name = "BOARDS")
public class BoardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "boards_seq_gen")
    private Long id;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "AUTHOR_NAME", nullable = false)
    private String authorName;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Lob
    @Column(name = "TEXT", nullable = false)
    private String text;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public void update(String title, String text) {
        this.title = title;
        this.text = text;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (regDate == null) {
            regDate = now;
        }
        updateDate = now;
    }

    @PreUpdate
    void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
