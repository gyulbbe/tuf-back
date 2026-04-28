package io.github.gyulbbe.board.entity;

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
        name = "comments_seq_gen",
        sequenceName = "COMMENTS_SEQ",
        allocationSize = 1
)
@Table(name = "COMMENTS")
public class BoardCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comments_seq_gen")
    private Long id;

    @Column(name = "BOARD_ID", nullable = false)
    private Long boardId;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "AUTHOR_NAME", nullable = false)
    private String authorName;

    @Column(name = "PARENT_ID")
    private Long parentId;

    @Builder.Default
    @Column(name = "DEPTH", nullable = false)
    private Integer depth = 0;

    @Column(name = "CONTENT", nullable = false, length = 4000)
    private String content;

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public void updateContent(String content) {
        this.content = content;
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
