package io.github.gyulbbe.map.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "REG_DATE", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    public void update(String mapName, String image) {
        this.mapName = mapName;
        this.image = image;
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
