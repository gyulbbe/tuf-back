package io.github.gyulbbe.map.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "maps_seq_gen",
        sequenceName = "MAPS_SEQ",
        allocationSize = 1
)
@Table(name = "MAPS")
public class MapEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "maps_seq_gen")
    private Long id;

    @Column(name = "MAP_NAME", nullable = false)
    private String mapName;

    @Column(name = "IMAGE")
    private String image;
}
