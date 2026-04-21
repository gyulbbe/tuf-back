package io.github.gyulbbe.board.dto;

import lombok.Data;

@Data
public class BoardUpdateRequestDto {

    private String title;
    private String text;
}
