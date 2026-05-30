package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

@Data
public class EntrySubmissionPermissionsResponseDto {
    private boolean canSubmit;
    private boolean canDelete;
    private boolean canRestart;
    private Long myTeamId;
    private String myRole = "VIEWER";
}
