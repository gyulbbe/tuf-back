package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

@Data
public class EntrySubmissionSourceStatusResponseDto {
    private Long sourceRpsDraftSessionId;
    private long count;
    private boolean exists;
}
