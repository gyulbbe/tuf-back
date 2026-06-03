package io.github.gyulbbe.tournament.dto;

import lombok.Data;

@Data
public class TournamentCreateMapDefaultRequestDto {
    private String target;
    private Integer roundNo;
    private String matchRole;
    private Long mapId;
}
