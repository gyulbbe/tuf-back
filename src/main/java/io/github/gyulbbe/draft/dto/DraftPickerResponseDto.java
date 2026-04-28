package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftPickerResponseDto {
    private Long draftTeamId;
    private Long pickerUserId;
    private String pickerUserLoginId;
    private String pickerName;
}
