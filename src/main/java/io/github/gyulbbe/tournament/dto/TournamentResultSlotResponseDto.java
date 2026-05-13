package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TournamentResultSlotResponseDto {
    private Long id;
    private Long stageId;
    private Long groupId;
    private String resultKey;
    private String resultType;
    private Integer rankNo;
    private String label;
    private Long participantId;
    private TournamentParticipantResponseDto participant;
    private LocalDateTime decidedAt;
}
