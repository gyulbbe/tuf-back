package io.github.gyulbbe.tournament.dto;

import lombok.Data;

@Data
public class RaceSurvivalProgressSubmissionMatchRequestDto {
    private Integer matchOrder;
    private Long mapId;
    private Long slot1ParticipantId;
    private Long slot2ParticipantId;
    private Integer slot1Score;
    private Integer slot2Score;
}
