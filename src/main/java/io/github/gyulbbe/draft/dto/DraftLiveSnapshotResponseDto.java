package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DraftLiveSnapshotResponseDto {
    private DraftLiveSessionInfoResponseDto session;
    private DraftLiveCurrentTurnResponseDto currentTurn;
    private List<DraftLiveTeamResponseDto> teams = new ArrayList<>();
    private List<DraftCandidateResponseDto> availableCandidates = new ArrayList<>();
    private List<DraftCandidateResponseDto> pickedCandidates = new ArrayList<>();
    private List<DraftPickResponseDto> recentPicks = new ArrayList<>();
    private DraftLivePermissionsResponseDto permissions;
}
