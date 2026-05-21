package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RpsDraftPickResponseDto {
    private Long rpsDraftSessionId;
    private Long pickNo;
    private Long rpsDraftTeamId;
    private String rpsDraftTeamName;
    private Long candidateId;
    private String candidateName;
    private Long pickedByUserId;
    private String pickedByUserLoginId;
    private String pickedByUserName;
    private LocalDateTime pickedAt;
}
