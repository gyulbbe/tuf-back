package io.github.gyulbbe.tournament.dto;

import lombok.Data;

@Data
public class TournamentMatchParticipantsRequestDto {
    private Long slot1ParticipantId;
    private Long slot2ParticipantId;
}
