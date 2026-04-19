package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftPickerAssignRequestDto {
    private Long pickerUserId;
    private Long operatorUserId;

    public Long resolvePickerUserId() {
        return pickerUserId != null ? pickerUserId : operatorUserId;
    }
}
