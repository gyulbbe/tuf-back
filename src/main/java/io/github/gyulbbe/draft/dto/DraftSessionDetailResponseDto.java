package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DraftSessionDetailResponseDto {
    private Long id;
    private String title;
    private Long ownerUserId;
    private String ownerUserLoginId;
    private String ownerName;
    private String status;
    private String orderMode;
    private Integer teamCount;
    private Integer pickTimeSeconds;
    private Integer currentPickNo;
    private Long currentDraftTeamId;
    private LocalDateTime deadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private List<DraftTeamResponseDto> teams = new ArrayList<>();
    private List<DraftCandidateResponseDto> candidates = new ArrayList<>();
    private List<DraftOrderResponseDto> orders = new ArrayList<>();
    private List<DraftPickResponseDto> picks = new ArrayList<>();
}
