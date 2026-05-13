package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TournamentMatchSlotResponseDto {
    private Long id;
    private Integer slotNo;
    private Long participantId;
    private TournamentParticipantResponseDto participant;
    private Long sourceMatchId;
    private String sourceOutcome;
    private String placeholderLabel;
    private Integer score;
    private Boolean isWinner;
    private Boolean isBye;
}
