package io.github.gyulbbe.board.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardCommentsSnapshotResponseDto {

    private Long boardId;
    private long commentCount;
    private List<BoardCommentResponseDto> comments;
}
