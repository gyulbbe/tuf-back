package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RpsDraftLiveSnapshotResponseDto {
    private RpsDraftLiveSessionInfoResponseDto session;
    private RpsDraftLiveRpsStateResponseDto rps;
    private List<RpsDraftLiveTeamResponseDto> teams = new ArrayList<>();
    private List<RpsDraftCandidateResponseDto> availableCandidates = new ArrayList<>();
    private List<RpsDraftCandidateResponseDto> pickedCandidates = new ArrayList<>();
    private List<RpsDraftPickResponseDto> recentPicks = new ArrayList<>();
    private RpsDraftLivePermissionsResponseDto permissions;
}
