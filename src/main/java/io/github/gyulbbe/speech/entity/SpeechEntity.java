package io.github.gyulbbe.speech.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "speeches_seq_gen",
        sequenceName = "SPEECHES_SEQ",
        allocationSize = 1
)
@Table(name = "SPEECHES")
public class SpeechEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "speeches_seq_gen")
    private Long id;

    @Column(name = "NICKNAME", nullable = false)
    private String nickname;

    @Column(name = "CHAT")
    private String chat;

    @Column(name = "CHAT_IMBEDDING_VECTOR", columnDefinition = "VECTOR")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private float[] chatEmbeddingVector;

    @CreationTimestamp
    @Column(name = "CHAT_DATETIME", updatable = false)
    private LocalDateTime chatDatetime;
}
