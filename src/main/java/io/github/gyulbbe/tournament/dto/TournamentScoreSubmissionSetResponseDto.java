package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TournamentScoreSubmissionSetResponseDto {
    private Integer setNo;
    private Integer winnerSlotNo;
    private Long mapId;
    private String mapName;
}
