package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RpsDraftCandidateResponseDto {
    private Long id;
    private Long rpsDraftSessionId;
    private String candidateName;
    private Integer displayOrder;
    private String status;
    private Long pickedRpsDraftTeamId;
    private String pickedRpsDraftTeamName;
    private LocalDateTime pickedAt;
}
