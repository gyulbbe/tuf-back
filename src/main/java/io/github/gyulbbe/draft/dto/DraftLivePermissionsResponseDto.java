package io.github.gyulbbe.draft.dto;

import lombok.Data;

@Data
public class DraftLivePermissionsResponseDto {
    private boolean canControl;
    private boolean canPick;
    private Long myTeamId;
    private String myRole;
}
