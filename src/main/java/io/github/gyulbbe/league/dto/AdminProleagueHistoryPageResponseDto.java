package io.github.gyulbbe.league.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminProleagueHistoryPageResponseDto {
    private List<AdminProleagueHistoryResponseDto> items = new ArrayList<>();
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
}
