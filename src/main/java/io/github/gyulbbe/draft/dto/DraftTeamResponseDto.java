package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DraftTeamResponseDto {
    private Long id;
    private Long draftSessionId;
    private String teamName;
    private Integer displayOrder;
    private List<DraftTeamOperatorResponseDto> operators = new ArrayList<>();
}
