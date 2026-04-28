package io.github.gyulbbe.common.utils.trace;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "traces_seq_gen",
        sequenceName = "TRACES_SEQ",
        allocationSize = 1
)
@Table(name = "TRACES")
public class TraceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "traces_seq_gen")
    private Long id;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "TYPE")
    private String type;

    @Column(name = "TEXT")
    private String text;

    @Column(name = "CREATED_DATETIME")
    private LocalDateTime createdDatetime;
}
