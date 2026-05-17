package io.github.gyulbbe.map.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminMapResponse {
    private Long id;
    private String mapName;
    private String image;
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
}
