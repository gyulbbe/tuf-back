package io.github.gyulbbe.draft.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftLivePreviewPayloadDto {
    private Long candidateUserId;
    private DraftLivePreviewPhase phase;
    private DraftLivePreviewEndReason endReason;
    private DraftLiveNormalizedPositionDto cursorPosition;
    private DraftLiveNormalizedPositionDto cardPosition;
}
