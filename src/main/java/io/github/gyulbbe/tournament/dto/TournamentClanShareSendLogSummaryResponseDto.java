package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TournamentClanShareSendLogSummaryResponseDto {
    private boolean hasHistory;
    private long totalCount;
    private LocalDateTime latestSentAt;
}
