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
public class BoardListResponseDto {

    private List<BoardSummaryResponseDto> boards;
    private BoardPaginationResponseDto pagination;
}
