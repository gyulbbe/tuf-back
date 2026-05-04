package io.github.gyulbbe.transcript.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TranscriptResponseDto {

    private String jobId;
    private TranscriptJobStatus status;
    private String sourceId;
    private String transcript;
    private String outputFile;
    private Long elapsedSeconds;
    private String errorMessage;
}
