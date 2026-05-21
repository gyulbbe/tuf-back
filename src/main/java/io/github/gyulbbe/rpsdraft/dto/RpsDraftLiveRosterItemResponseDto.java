package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RpsDraftLiveRosterItemResponseDto {
    private Long pickNo;
    private Long roundNo;
    private Long candidateId;
    private String candidateName;
    private Long pickedByUserId;
    private String pickedByUserLoginId;
    private String pickedByUserName;
    private LocalDateTime pickedAt;
}
