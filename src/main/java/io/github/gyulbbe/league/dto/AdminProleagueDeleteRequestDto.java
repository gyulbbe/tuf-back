package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminProleagueDeleteRequestDto {
    private List<Long> leagueIds = new ArrayList<>();
}
