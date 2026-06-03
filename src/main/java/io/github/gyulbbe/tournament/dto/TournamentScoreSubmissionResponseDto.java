package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TournamentScoreSubmissionResponseDto {
    private Long id;
    private Long submissionId;
    private Long tournamentId;
    private Long matchId;
    private Long submittedByUserId;
    private Long submittedByParticipantId;
    private String submitterLoginId;
    private String submitterRole;
    private Integer slot1Score;
    private Integer slot2Score;
    private Integer winnerSlotNo;
    private Long mapId;
    private String status;
    private Long adminReviewerUserId;
    private LocalDateTime adminReviewedAt;
    private String adminNote;
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
}
