package io.github.gyulbbe.tournament.dto;

import lombok.Data;

@Data
public class TournamentCreateSlotRequestDto {
    private Integer slotNo;
    private Long userId;
    private String participantName;
}
