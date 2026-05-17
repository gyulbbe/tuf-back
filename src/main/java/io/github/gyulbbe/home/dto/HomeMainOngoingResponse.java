package io.github.gyulbbe.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeMainOngoingResponse {
    private String type;
    private Long id;
    private String title;
    private String status;
    private String primaryText;
    private String secondaryText;
    private LocalDateTime updatedAt;
}
