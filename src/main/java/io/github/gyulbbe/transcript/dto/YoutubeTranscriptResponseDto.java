package io.github.gyulbbe.transcript.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class YoutubeTranscriptResponseDto {

    private String videoId;
    private String transcript;
    private String outputFile;
    private long elapsedSeconds;
}
