package io.github.gyulbbe.home.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class HomeScheduleResponse {
    private Long id;
    private String scheduleGroup;
    private String timeLabel;
    private String title;
    private String description;
    private LocalDateTime scheduledAt;
    private String targetUrl;
    private String linkType;
    private String navigationUrl;

    @Builder.Default
    private List<HomeScheduleMatchResponse> matches = new ArrayList<>();
}
