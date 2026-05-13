package io.github.gyulbbe.draft.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DraftSessionDeleteRequestDto {
    private List<Long> sessionIds = new ArrayList<>();
}
