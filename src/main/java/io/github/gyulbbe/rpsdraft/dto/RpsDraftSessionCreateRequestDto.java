package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

import java.util.List;

@Data
public class RpsDraftSessionCreateRequestDto {
    private String title;
    private Long team1PickerUserId;
    private Long team2PickerUserId;
    private List<String> candidateNames;
}
