package io.github.gyulbbe.home.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminHomeScheduleMapSearchResponse {
    private Long id;
    private String mapName;
    private String image;
}
