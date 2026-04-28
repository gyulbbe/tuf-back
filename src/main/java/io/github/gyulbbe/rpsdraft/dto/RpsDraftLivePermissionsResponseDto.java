package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

@Data
public class RpsDraftLivePermissionsResponseDto {
    private boolean canControl;
    private boolean canSubmitRps;
    private boolean canPick;
    private Long myTeamId;
    private String myRole;
}
