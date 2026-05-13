package io.github.gyulbbe.tournament.dto;

import lombok.Data;

import java.util.List;

@Data
public class TournamentScoreSubmissionRequestDto {
    private List<TournamentMatchScoreRequestDto> scores;
}
