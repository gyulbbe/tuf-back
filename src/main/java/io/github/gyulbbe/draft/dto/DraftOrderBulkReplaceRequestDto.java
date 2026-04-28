package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DraftOrderBulkReplaceRequestDto {
    private List<DraftOrderRequestDto> orders = new ArrayList<>();
}
