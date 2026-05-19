package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RaceSurvivalProgressSubmissionMatchResponseDto {
    private Long id;
    private Integer matchOrder;
    private Long mapId;
    private String mapName;
    private Long slot1ParticipantId;
    private TournamentParticipantResponseDto slot1Participant;
    private String slot1Race;
    private Long slot2ParticipantId;
    private TournamentParticipantResponseDto slot2Participant;
    private String slot2Race;
    private Integer slot1Score;
    private Integer slot2Score;
    private Long winnerParticipantId;
    private TournamentParticipantResponseDto winnerParticipant;
}
