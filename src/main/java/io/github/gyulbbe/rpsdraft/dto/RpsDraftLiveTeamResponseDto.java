package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RpsDraftLiveTeamResponseDto {
    private Long id;
    private Long rpsDraftSessionId;
    private String teamName;
    private Integer displayOrder;
    private Long pickerUserId;
    private String pickerName;
    private List<RpsDraftLiveRosterItemResponseDto> roster = new ArrayList<>();
}
