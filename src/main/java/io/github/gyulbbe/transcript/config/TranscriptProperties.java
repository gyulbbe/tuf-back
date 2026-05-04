package io.github.gyulbbe.transcript.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "transcript")
public class TranscriptProperties {

    private String ytDlpPath;
    private String ffmpegPath;
    private String whisperCliPath;
    private String whisperModelPath;
    private String whisperLanguage;
    private Integer whisperThreads;
    private Integer whisperNice;
    private String workDir;
    private String outputDir;
    private String logDir;
}
