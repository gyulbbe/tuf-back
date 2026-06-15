package io.github.gyulbbe.tournament.dto;

import lombok.Data;

import java.util.List;

@Data
public class TournamentScoreSubmissionRequestDto {
    private Integer bestOf;
    private Long mapId;
    private List<TournamentMatchScoreRequestDto> scores;
    private List<TournamentScoreSubmissionSetRequestDto> sets;
}
