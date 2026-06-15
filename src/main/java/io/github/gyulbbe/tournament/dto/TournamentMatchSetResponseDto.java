package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TournamentMatchSetResponseDto {
    private Integer setNo;
    private Long mapId;
    private String mapName;
    private Integer winnerSlotNo;
}
