package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TournamentGroupResponseDto {
    private Long id;
    private Long stageId;
    private String groupCode;
    private String groupName;
    private Integer displayOrder;
    private String description;
    private List<TournamentParticipantResponseDto> participants;
    private List<TournamentMatchResponseDto> matches;
    private List<TournamentResultSlotResponseDto> resultSlots;
}
