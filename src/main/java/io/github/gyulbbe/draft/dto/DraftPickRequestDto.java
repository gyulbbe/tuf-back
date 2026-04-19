package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DraftPickRequestDto {
    private Long draftSessionId;
    private Integer roundNo;
    private Long pickNo;
    private Long draftTeamId;
    private Long candidateUserId;
    private Long pickedByUserId;
    private LocalDateTime pickedAt;
}
