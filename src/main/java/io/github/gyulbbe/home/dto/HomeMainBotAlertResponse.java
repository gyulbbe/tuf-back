package io.github.gyulbbe.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeMainBotAlertResponse {
    private String type;
    private String message;
    private Long sourceId;
}
