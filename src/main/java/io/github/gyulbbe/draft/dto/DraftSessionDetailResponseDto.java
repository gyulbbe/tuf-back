package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DraftSessionDetailResponseDto {
    private Long id;
    private String title;
    private String status;
    private Integer teamCount;
    private Integer pickTimeSeconds;
    private String draftMode;
    private Integer currentPickNo;
    private Long currentDraftTeamId;
    private java.time.LocalDateTime deadlineAt;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime endedAt;
    private List<DraftTeamResponseDto> teams = new ArrayList<>();
    private List<DraftCandidateResponseDto> candidates = new ArrayList<>();
    private List<DraftOrderResponseDto> orders = new ArrayList<>();
    private List<DraftPickResponseDto> picks = new ArrayList<>();
}
