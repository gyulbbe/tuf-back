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
public class BoardDetailResponseDto {

    private Long id;
    private String authorUserId;
    @Deprecated
    private String authorName;
    private String title;
    private String text;
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
    private long commentCount;
    private boolean editable;
    private boolean deletable;
    private List<BoardCommentResponseDto> comments;
}
