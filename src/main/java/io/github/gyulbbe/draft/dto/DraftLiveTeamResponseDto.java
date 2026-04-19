package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DraftLiveTeamResponseDto {
    private Long id;
    private Long draftSessionId;
    private String teamName;
    private Integer displayOrder;
    private Long pickerUserId;
    private String pickerName;
    private List<DraftLiveRosterItemResponseDto> roster = new ArrayList<>();
}
