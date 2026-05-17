package io.github.gyulbbe.home.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class HomeScheduleMatchResponse {
    private Long id;
    private Integer displayOrder;
    private String setLabel;
    private String matchFormat;
    private String teamAName;
    private String teamBName;
    private Long mapId;
    private String mapName;
    private String note;

    @Builder.Default
    private List<HomeScheduleMatchPlayerResponse> sideAPlayers = new ArrayList<>();

    @Builder.Default
    private List<HomeScheduleMatchPlayerResponse> sideBPlayers = new ArrayList<>();
}
