package io.github.gyulbbe.transcript.service;

import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.common.error.ApiException;
import io.github.gyulbbe.transcript.config.TranscriptProperties;
import io.github.gyulbbe.transcript.dto.TranscriptRequestDto;
import io.github.gyulbbe.transcript.dto.TranscriptResponseDto;
import io.github.gyulbbe.transcript.service.ExternalProcessRunner.ExternalProcessException;
import io.github.gyulbbe.transcript.service.ExternalProcessRunner.ProcessResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptService {

    private static final Duration SOURCE_ID_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration FFMPEG_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration WHISPER_TIMEOUT = Duration.ofHours(2);
    private static final DateTimeFormatter JOB_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TranscriptProperties properties;
    private final ExternalProcessRunner processRunner;

    public TranscriptResponseDto createTranscript(TranscriptRequestDto requestDto) {
        Instant startedAt = Instant.now();
        String url = normalizeSoopVodUrl(validateSoopVodUrl(requestDto));
        validateRequiredProperties();
        String jobId = createJobId();
        Path workRoot = Path.of(properties.getWorkDir());
        Path jobDir = workRoot.resolve(jobId);
        Path outputDir = Path.of(properties.getOutputDir());

        try {
            Files.createDirectories(jobDir);
            Files.createDirectories(outputDir);

            String sourceId = fetchSourceId(jobId, url, jobDir);
            Path downloadedAudio = downloadAudio(jobId, url, jobDir);
            Path wavAudio = convertToWhisperWav(jobId, downloadedAudio, jobDir);
            Path transcriptFile = runWhisper(jobId, wavAudio, outputDir);
            String transcript = Files.readString(transcriptFile, StandardCharsets.UTF_8);

            // TODO: MVP에서는 작업 파일을 남긴다. 운영 전에는 오래된 work-dir 작업 폴더 정리 정책을 추가해야 한다.
            long elapsedSeconds = Duration.between(startedAt, Instant.now()).toSeconds();
            return TranscriptResponseDto.builder()
                    .sourceId(sourceId)
                    .transcript(transcript)
                    .outputFile(transcriptFile.toString())
                    .elapsedSeconds(elapsedSeconds)
                    .build();
        } catch (IOException e) {
            log.error("transcript file handling failed. jobId={}", jobId, e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 작업 파일을 처리하지 못했습니다.");
        }
    }

    private String validateSoopVodUrl(TranscriptRequestDto requestDto) {
        if (requestDto == null || requestDto.getUrl() == null || requestDto.getUrl().isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "영상 URL을 입력해 주세요.");
        }

        String url = requestDto.getUrl().trim();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "올바른 영상 URL 형식이 아닙니다.");
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "올바른 영상 URL 형식이 아닙니다.");
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean allowedScheme = "http".equals(normalizedScheme) || "https".equals(normalizedScheme);
        if (!allowedScheme || !isSoopVodHost(normalizedHost)) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "현재는 SOOP VOD URL만 입력할 수 있습니다. vod.sooplive.com 또는 vod.sooplive.co.kr 주소를 사용해 주세요.");
        }

        return url;
    }

    private String normalizeSoopVodUrl(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (host != null && "vod.sooplive.com".equals(host.toLowerCase(Locale.ROOT))) {
            return url.replaceFirst("(?i)://vod\\.sooplive\\.com", "://vod.sooplive.co.kr");
        }
        return url;
    }

    private boolean isSoopVodHost(String host) {
        return "vod.sooplive.com".equals(host) || "vod.sooplive.co.kr".equals(host);
    }

    private void validateRequiredProperties() {
        requireText("transcript.yt-dlp-path", properties.getYtDlpPath());
        requireText("transcript.ffmpeg-path", properties.getFfmpegPath());
        requireText("transcript.whisper-cli-path", properties.getWhisperCliPath());
        requireText("transcript.whisper-model-path", properties.getWhisperModelPath());
        requireText("transcript.whisper-language", properties.getWhisperLanguage());
        requireText("transcript.work-dir", properties.getWorkDir());
        requireText("transcript.output-dir", properties.getOutputDir());
        requireText("transcript.log-dir", properties.getLogDir());
        requirePositive("transcript.whisper-threads", properties.getWhisperThreads());
        requireNiceValue("transcript.whisper-nice", properties.getWhisperNice());
    }

    private void requireText(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 설정이 누락되었습니다: " + key);
        }
    }

    private void requirePositive(String key, Integer value) {
        if (value == null || value < 1) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 설정값이 올바르지 않습니다: " + key);
        }
    }

    private void requireNiceValue(String key, Integer value) {
        if (value == null || value < 0 || value > 19) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 설정값이 올바르지 않습니다: " + key);
        }
    }

    private String fetchSourceId(String jobId, String url, Path jobDir) {
        ProcessResult result = runExternalCommand(
                jobId,
                "source-id",
                List.of(
                        properties.getYtDlpPath(),
                        "--no-playlist",
                        "--print", "id",
                        "--skip-download",
                        "--no-warnings",
                        url
                ),
                jobDir,
                SOURCE_ID_TIMEOUT,
                "영상 정보를 확인하지 못했습니다. URL이 공개 VOD인지 확인해 주세요."
        );

        return result.stdout().lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.INTERNAL_ERROR,
                        "영상 ID를 확인하지 못했습니다."
                ));
    }

    private Path downloadAudio(String jobId, String url, Path jobDir) throws IOException {
        runExternalCommand(
                jobId,
                "download-audio",
                List.of(
                        properties.getYtDlpPath(),
                        "--no-playlist",
                        "--format", "bestaudio/best",
                        "--concat-playlist", "multi_video",
                        "--output", jobDir.resolve("source.%(ext)s").toString(),
                        url
                ),
                jobDir,
                DOWNLOAD_TIMEOUT,
                "영상 오디오 다운로드에 실패했습니다. URL을 확인하거나 잠시 후 다시 시도해 주세요."
        );

        try (Stream<Path> files = Files.list(jobDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(".part"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.INTERNAL_ERROR,
                            "영상 오디오 다운로드 파일을 찾지 못했습니다."
                    ));
        }
    }

    private Path convertToWhisperWav(String jobId, Path sourceAudio, Path jobDir) {
        Path wavAudio = jobDir.resolve("audio-16k-mono.wav");
        runExternalCommand(
                jobId,
                "ffmpeg-wav",
                List.of(
                        properties.getFfmpegPath(),
                        "-y",
                        "-i", sourceAudio.toString(),
                        "-ar", "16000",
                        "-ac", "1",
                        "-c:a", "pcm_s16le",
                        wavAudio.toString()
                ),
                jobDir,
                FFMPEG_TIMEOUT,
                "오디오를 whisper.cpp 입력 형식으로 변환하지 못했습니다."
        );
        return wavAudio;
    }

    private Path runWhisper(String jobId, Path wavAudio, Path outputDir) {
        Path outputBase = outputDir.resolve(jobId);
        Path transcriptFile = outputDir.resolve(jobId + ".txt");
        runExternalCommand(
                jobId,
                "whisper",
                List.of(
                        "nice",
                        "-n", String.valueOf(properties.getWhisperNice()),
                        properties.getWhisperCliPath(),
                        "-m", properties.getWhisperModelPath(),
                        "-f", wavAudio.toString(),
                        "-l", properties.getWhisperLanguage(),
                        "-t", String.valueOf(properties.getWhisperThreads()),
                        "-otxt",
                        "-of", outputBase.toString()
                ),
                outputDir,
                WHISPER_TIMEOUT,
                "음성 인식 스크립트 생성에 실패했습니다."
        );

        if (!Files.exists(transcriptFile)) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "whisper.cpp 결과 txt 파일을 찾지 못했습니다.");
        }
        return transcriptFile;
    }

    private ProcessResult runExternalCommand(
            String jobId,
            String step,
            List<String> command,
            Path workingDirectory,
            Duration timeout,
            String userMessage
    ) {
        try {
            return processRunner.run(jobId, step, command, workingDirectory, timeout);
        } catch (ExternalProcessException e) {
            log.error("external command failed. jobId={}, step={}, exitCode={}, stdoutLog={}, stderrLog={}",
                    jobId, step, e.getExitCode(), e.getStdoutLog(), e.getStderrLog(), e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, userMessage);
        }
    }

    private String createJobId() {
        String timestamp = LocalDateTime.now().format(JOB_TIME_FORMATTER);
        String random = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "-" + random;
    }
}
