package io.github.gyulbbe.commentary.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "commentaries_seq_gen",
        sequenceName = "COMMENTARIES_SEQ",
        allocationSize = 1
)
@Table(name = "COMMENTARIES")
public class CommentaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "commentaries_seq_gen")
    private Long id;

    @Column(name = "MATCH_INFO_ID", nullable = false)
    private Long matchInfoId;

    @Column(name = "MATCH_SUMMARY")
    private String matchSummary;
}
