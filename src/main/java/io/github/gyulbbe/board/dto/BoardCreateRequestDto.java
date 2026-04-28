package io.github.gyulbbe.board.dto;

import lombok.Data;

@Data
public class BoardCreateRequestDto {

    private String authorName;
    private String title;
    private String text;
}
