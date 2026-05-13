package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TournamentStageResponseDto {
    private Long id;
    private Integer stageNo;
    private String stageName;
    private String stageType;
    private String status;
    private Integer displayOrder;
    private List<TournamentGroupResponseDto> groups;
}
