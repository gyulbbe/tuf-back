package io.github.gyulbbe.tournament.dto;

import lombok.Data;

import java.util.List;

@Data
public class TournamentScoreSubmissionRequestDto {
    private Long mapId;
    private List<TournamentMatchScoreRequestDto> scores;
}
