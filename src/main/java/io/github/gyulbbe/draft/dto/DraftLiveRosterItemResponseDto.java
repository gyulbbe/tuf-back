package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DraftLiveRosterItemResponseDto {
    private Long pickNo;
    private Long candidateUserId;
    private String candidateName;
    private Long pickedByUserId;
    private String pickedByUserName;
    private LocalDateTime pickedAt;
}
