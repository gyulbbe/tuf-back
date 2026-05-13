package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TournamentParticipantResponseDto {
    private Long id;
    private Long userId;
    private String userLoginId;
    private String participantName;
    private String displayName;
    private Integer seedNo;
    private String seedLabel;
    private String status;
}
