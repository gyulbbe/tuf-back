package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

@Data
public class RpsDraftLiveRpsStateResponseDto {
    private boolean team1Submitted;
    private boolean team2Submitted;
    private String team1Choice;
    private String team2Choice;
    private String result;
}
