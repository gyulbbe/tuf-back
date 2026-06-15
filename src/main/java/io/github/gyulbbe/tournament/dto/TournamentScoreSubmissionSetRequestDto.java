package io.github.gyulbbe.tournament.dto;

import lombok.Data;

@Data
public class TournamentScoreSubmissionSetRequestDto {
    private Integer setNo;
    private Integer winnerSlotNo;
    private Long mapId;
}
