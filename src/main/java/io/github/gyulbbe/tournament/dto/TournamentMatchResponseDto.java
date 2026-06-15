package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TournamentMatchResponseDto {
    private Long id;
    private Long stageId;
    private Long groupId;
    private String matchKey;
    private String matchRole;
    private Integer roundNo;
    private Integer matchNo;
    private String displayName;
    private Integer bestOf;
    private String status;
    private Long winnerParticipantId;
    private Long mapId;
    private String mapName;
    private List<TournamentMatchSetResponseDto> setResults;
    private LocalDateTime scheduledAt;
    private Integer layoutCol;
    private Integer layoutRow;
    private Integer displayOrder;
    private List<TournamentMatchSlotResponseDto> slots;
}
