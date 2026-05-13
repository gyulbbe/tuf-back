package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.util.List;

@Data
public class DraftHistoryPageResponseDto {
    private List<DraftSessionSummaryResponseDto> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
}
