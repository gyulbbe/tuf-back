package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TournamentSummaryResponseDto {
    private Long id;
    private String title;
    private String bracketType;
    private String status;
    private int groupCount;
    private int participantCount;
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
}
