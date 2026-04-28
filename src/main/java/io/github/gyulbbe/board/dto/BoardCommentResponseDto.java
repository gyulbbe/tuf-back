package io.github.gyulbbe.board.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardCommentResponseDto {

    private Long id;
    private Long parentId;
    private Integer depth;
    private String authorUserId;
    @Deprecated
    private String authorName;
    private String content;
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
    private boolean editable;
    private boolean deletable;
    private List<BoardCommentResponseDto> children;
}
