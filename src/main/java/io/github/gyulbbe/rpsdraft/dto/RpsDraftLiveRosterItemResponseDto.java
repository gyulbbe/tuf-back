package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RpsDraftLiveRosterItemResponseDto {
    private Long pickNo;
    private Long candidateUserId;
    private String candidateName;
    private Long pickedByUserId;
    private String pickedByUserName;
    private LocalDateTime pickedAt;
}
