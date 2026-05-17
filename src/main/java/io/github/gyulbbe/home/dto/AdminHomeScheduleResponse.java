package io.github.gyulbbe.home.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AdminHomeScheduleResponse {
    private Long id;
    private String scheduleGroup;
    private String title;
    private String description;
    private LocalDateTime scheduledAt;
    private String targetUrl;
    private String linkType;
    private Integer displayPriority;
    private String status;
    private LocalDateTime regDate;
    private LocalDateTime updateDate;

    @Builder.Default
    private List<HomeScheduleMatchResponse> matches = new ArrayList<>();
}
