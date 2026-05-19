package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RaceSurvivalProgressSubmissionResponseDto {
    private Long id;
    private Long tournamentId;
    private Long submittedByUserId;
    private String submitterLoginId;
    private String status;
    private Long reviewedByUserId;
    private String reviewerLoginId;
    private String adminNote;
    private LocalDateTime regDate;
    private LocalDateTime reviewedAt;
    private List<RaceSurvivalProgressSubmissionMatchResponseDto> matches;
}
