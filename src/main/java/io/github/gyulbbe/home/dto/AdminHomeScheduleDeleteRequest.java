package io.github.gyulbbe.home.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminHomeScheduleDeleteRequest {
    private List<Long> scheduleIds;
}
