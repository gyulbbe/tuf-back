package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

@Data
public class RpsDraftSessionCreateRequestDto {
    private String title;
    private String team1Name;
    private String team2Name;
}
