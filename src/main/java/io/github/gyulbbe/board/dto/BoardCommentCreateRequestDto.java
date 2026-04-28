package io.github.gyulbbe.board.dto;

import lombok.Data;

@Data
public class BoardCommentCreateRequestDto {

    private String authorName;
    private Long parentId;
    private String content;
}
