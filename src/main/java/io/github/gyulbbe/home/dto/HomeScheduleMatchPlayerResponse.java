package io.github.gyulbbe.home.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HomeScheduleMatchPlayerResponse {
    private Long id;
    private String side;
    private Integer slotOrder;
    private Long userId;
    private String playerName;
    private String playerRank;
    private String playerRace;
    private String note;
}
