package io.github.gyulbbe.home.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminHomeScheduleDeleteResponse {
    private int deletedCount;
}
