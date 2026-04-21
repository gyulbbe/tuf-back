package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RpsDraftCandidateResponseDto {
    private Long rpsDraftSessionId;
    private Long candidateUserId;
    private String candidateName;
    private String race;
    private String status;
    private Long pickedRpsDraftTeamId;
    private String pickedRpsDraftTeamName;
    private LocalDateTime pickedAt;
}
