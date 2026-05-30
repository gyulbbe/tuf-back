package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EntrySubmissionSnapshotResponseDto {
    private EntrySubmissionSessionInfoResponseDto session;
    private List<EntrySubmissionTeamResponseDto> teams = new ArrayList<>();
    private List<EntrySubmissionPlayerResponseDto> players = new ArrayList<>();
    private List<EntrySubmissionEntryResponseDto> entries = new ArrayList<>();
    private List<EntrySubmissionMatchResponseDto> matches = new ArrayList<>();
    private EntrySubmissionPermissionsResponseDto permissions;
}
