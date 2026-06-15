package io.github.gyulbbe.tournament.dto;

import lombok.Data;

import java.util.List;

@Data
public class TournamentCreateMatchDefaultRequestDto {
    private String target;
    private Integer roundNo;
    private String matchRole;
    private Integer bestOf;
    private List<Long> mapIds;
}
