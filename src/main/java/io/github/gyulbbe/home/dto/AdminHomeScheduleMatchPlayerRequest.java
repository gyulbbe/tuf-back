package io.github.gyulbbe.home.dto;

import lombok.Data;

@Data
public class AdminHomeScheduleMatchPlayerRequest {
    private Long id;
    private String side;
    private Integer slotOrder;
    private Long userId;
    private String playerName;
    private String playerRank;
    private String playerRace;
    private String note;
}
