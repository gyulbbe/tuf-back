package io.github.gyulbbe.tournament.dto;

import lombok.Data;

import java.util.List;

@Data
public class TournamentCreateGroupRequestDto {
    private String groupCode;
    private String groupName;
    private List<TournamentCreateSlotRequestDto> slots;
}
