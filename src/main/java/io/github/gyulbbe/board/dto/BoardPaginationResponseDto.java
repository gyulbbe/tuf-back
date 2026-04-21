package io.github.gyulbbe.board.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardPaginationResponseDto {

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasPreviousPage;
    private boolean hasNextPage;
    private Integer previousPage;
    private Integer nextPage;
    private int groupStartPage;
    private int groupEndPage;
    private boolean hasPreviousGroup;
    private boolean hasNextGroup;
    private Integer previousGroupPage;
    private Integer nextGroupPage;
    private Integer firstPage;
    private Integer lastPage;
}
