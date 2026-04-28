package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

@Data
public class RpsDraftPickerResponseDto {
    private Long rpsDraftTeamId;
    private Long pickerUserId;
    private String pickerUserLoginId;
    private String pickerName;
}
