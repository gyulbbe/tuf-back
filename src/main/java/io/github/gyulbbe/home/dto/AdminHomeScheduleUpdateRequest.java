package io.github.gyulbbe.home.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminHomeScheduleUpdateRequest {
    private String scheduleGroup;
    private String title;
    private String description;
    private LocalDateTime scheduledAt;
    private String targetUrl;
    private String linkType;
    private Integer displayPriority;
    private List<AdminHomeScheduleMatchRequest> matches;
}
