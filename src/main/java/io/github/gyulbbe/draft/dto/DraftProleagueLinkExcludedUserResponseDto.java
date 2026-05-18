package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftProleagueLinkExcludedUserResponseDto {
    private Long userId;
    private String userLoginId;
    private String reason;
}
