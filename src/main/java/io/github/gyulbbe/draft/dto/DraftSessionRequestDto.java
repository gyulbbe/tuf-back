package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DraftSessionRequestDto {
    private String title;
    private String status;
    private Long proleagueId;
    private String orderMode;
    private Integer teamCount;
    private Integer pickTimeSeconds;
    private Integer currentPickNo;
    private Long currentDraftTeamId;
    private LocalDateTime deadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private List<DraftProleagueTeamPickerRequestDto> proleagueTeamPickers = new ArrayList<>();
}
