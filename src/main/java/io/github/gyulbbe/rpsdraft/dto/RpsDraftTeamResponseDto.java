package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

@Data
public class RpsDraftTeamResponseDto {
    private Long id;
    private Long rpsDraftSessionId;
    private String teamName;
    private Integer displayOrder;
    private Long pickerUserId;
    private String pickerName;
}
