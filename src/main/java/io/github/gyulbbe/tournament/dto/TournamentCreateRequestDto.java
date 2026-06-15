package io.github.gyulbbe.tournament.dto;

import lombok.Data;

import java.util.List;

@Data
public class TournamentCreateRequestDto {
    private String title;
    private String bracketType;
    private Integer bestOf;
    private Boolean publishNow;
    private List<TournamentCreateMapDefaultRequestDto> mapDefaults;
    private List<TournamentCreateMatchDefaultRequestDto> matchDefaults;
    private List<TournamentCreateGroupRequestDto> groups;
}
