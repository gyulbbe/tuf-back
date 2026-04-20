package io.github.gyulbbe.draft.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftLiveNormalizedPositionDto {
    private Double x;
    private Double y;
}
