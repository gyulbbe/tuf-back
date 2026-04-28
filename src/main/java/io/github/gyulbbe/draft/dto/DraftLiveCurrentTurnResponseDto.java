package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftLiveCurrentTurnResponseDto {
    private Long pickNo;
    private Long teamId;
    private String teamName;
    private Long remainingSeconds;
}
