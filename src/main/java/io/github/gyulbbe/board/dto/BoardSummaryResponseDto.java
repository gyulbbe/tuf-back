package io.github.gyulbbe.board.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardSummaryResponseDto {

    private Long id;
    private String authorName;
    private String title;
    private String summaryText;
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
    private boolean editable;
    private boolean deletable;
}
