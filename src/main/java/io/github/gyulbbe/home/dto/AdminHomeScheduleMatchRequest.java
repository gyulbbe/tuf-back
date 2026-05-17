package io.github.gyulbbe.home.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminHomeScheduleMatchRequest {
    private Long id;
    private Integer displayOrder;
    private String setLabel;
    private String matchFormat;
    private String teamAName;
    private String teamBName;
    private Long mapId;
    private String note;
    private List<AdminHomeScheduleMatchPlayerRequest> players;
}
