package io.github.gyulbbe.transcript.service;

import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.common.error.ApiException;
import io.github.gyulbbe.transcript.config.TranscriptProperties;
import io.github.gyulbbe.transcript.dto.TranscriptJobStatus;
import io.github.gyulbbe.transcript.dto.TranscriptRequestDto;
import io.github.gyulbbe.transcript.dto.TranscriptResponseDto;
import io.github.gyulbbe.transcript.service.ExternalProcessRunner.ExternalProcessException;
import io.github.gyulbbe.transcript.service.ExternalProcessRunner.ProcessResult;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptService {

    private static final Duration SOURCE_ID_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration FFMPEG_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration WHISPER_TIMEOUT = Duration.ofHours(2);
    private static final Duration JOB_RETENTION = Duration.ofHours(6);
    private static final DateTimeFormatter JOB_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TranscriptProperties properties;
    private final ExternalProcessRunner processRunner;
    private final ConcurrentHashMap<String, TranscriptJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService transcriptExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "transcript-worker");
        thread.setDaemon(true);
        return thread;
    });

    public TranscriptResponseDto createTranscriptJob(TranscriptRequestDto requestDto) {
        String url = normalizeSoopVodUrl(validateSoopVodUrl(requestDto));
        validateRequiredProperties();
        cleanupExpiredJobs();

        String jobId = createJobId();
        TranscriptJob job = new TranscriptJob(jobId, url, Instant.now());
        jobs.put(jobId, job);

        try {
            transcriptExecutor.submit(() -> processTranscriptJob(jobId));
        } catch (RejectedExecutionException e) {
            jobs.remove(jobId);
            log.error("transcript job submit failed. jobId={}, url={}", jobId, url, e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 작업을 시작하지 못했습니다.");
        }

        log.info("transcript job accepted. jobId={}, status={}, url={}", jobId, job.status, url);
        return toResponse(job, false);
    }

    public TranscriptResponseDto getTranscriptJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "작업 ID를 입력해 주세요.");
        }

        TranscriptJob job = jobs.get(jobId);
        if (job == null) {
            log.warn("transcript job not found. jobId={}", jobId);
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "스크립트 작업을 찾지 못했습니다.");
        }

        return toResponse(job, true);
    }

    @PreDestroy
    public void shutdown() {
        transcriptExecutor.shutdownNow();
    }

    private void processTranscriptJob(String jobId) {
        TranscriptJob job = jobs.get(jobId);
        if (job == null) {
            log.warn("transcript job skipped because state is missing. jobId={}", jobId);
            return;
        }

        job.status = TranscriptJobStatus.RUNNING;
        job.startedAt = Instant.now();
        log.info("transcript job started. jobId={}, url={}", jobId, job.url);

        Path workRoot = Path.of(properties.getWorkDir());
        Path jobDir = workRoot.resolve(jobId);
        Path outputDir = Path.of(properties.getOutputDir());

        try {
            createJobDirectories(jobId, jobDir, outputDir);

            job.sourceId = fetchSourceId(jobId, job.url, jobDir);
            Path downloadedAudio = downloadAudio(jobId, job.url, jobDir);
            Path wavAudio = convertToWhisperWav(jobId, downloadedAudio, jobDir);
            Path transcriptFile = runWhisper(jobId, wavAudio, outputDir);

            // TODO: MVP에서는 작업 파일을 남긴다. 운영 전에는 오래된 work-dir 작업 폴더 정리 정책을 추가해야 한다.
            job.outputFile = transcriptFile.toString();
            job.status = TranscriptJobStatus.SUCCEEDED;
            job.finishedAt = Instant.now();
            log.info("transcript job succeeded. jobId={}, sourceId={}, outputFile={}, elapsedSeconds={}",
                    jobId, job.sourceId, job.outputFile, elapsedSeconds(job));
        } catch (ApiException e) {
            failJob(job, e.getMessage(), e);
        } catch (Exception e) {
            failJob(job, "스크립트 생성 중 서버 오류가 발생했습니다.", e);
        }
    }

    private void createJobDirectories(String jobId, Path jobDir, Path outputDir) {
        try {
            Files.createDirectories(jobDir);
            Files.createDirectories(outputDir);
            log.info("transcript job directories ready. jobId={}, jobDir={}, outputDir={}", jobId, jobDir, outputDir);
        } catch (IOException e) {
            log.error("transcript job directory creation failed. jobId={}, jobDir={}, outputDir={}",
                    jobId, jobDir, outputDir, e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 작업 폴더를 생성하지 못했습니다.");
        }
    }

    private void failJob(TranscriptJob job, String message, Exception e) {
        job.status = TranscriptJobStatus.FAILED;
        job.errorMessage = message;
        job.finishedAt = Instant.now();
        log.error("transcript job failed. jobId={}, sourceId={}, url={}, elapsedSeconds={}, message={}",
                job.jobId, job.sourceId, job.url, elapsedSeconds(job), message, e);
    }

    private String validateSoopVodUrl(TranscriptRequestDto requestDto) {
        if (requestDto == null || requestDto.getUrl() == null || requestDto.getUrl().isBlank()) {
            log.warn("transcript request rejected. reason=blank-url");
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "영상 URL을 입력해 주세요.");
        }

        String url = requestDto.getUrl().trim();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            log.warn("transcript request rejected. reason=invalid-uri, url={}", url);
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "올바른 영상 URL 형식이 아닙니다.");
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            log.warn("transcript request rejected. reason=missing-scheme-or-host, url={}", url);
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "올바른 영상 URL 형식이 아닙니다.");
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean allowedScheme = "http".equals(normalizedScheme) || "https".equals(normalizedScheme);
        if (!allowedScheme || !isSoopVodHost(normalizedHost)) {
            log.warn("transcript request rejected. reason=unsupported-url, scheme={}, host={}, url={}",
                    normalizedScheme, normalizedHost, url);
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
            log.error("transcript property missing. key={}", key);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 설정이 누락되었습니다: " + key);
        }
    }

    private void requirePositive(String key, Integer value) {
        if (value == null || value < 1) {
            log.error("transcript property invalid. key={}, value={}", key, value);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 설정값이 올바르지 않습니다: " + key);
        }
    }

    private void requireNiceValue(String key, Integer value) {
        if (value == null || value < 0 || value > 19) {
            log.error("transcript property invalid. key={}, value={}", key, value);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 설정값이 올바르지 않습니다: " + key);
        }
    }

    private String fetchSourceId(String jobId, String url, Path jobDir) {
        log.info("transcript job step started. jobId={}, step=source-id", jobId);
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

        String sourceId = result.stdout().lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(null);
        if (sourceId == null) {
            log.error("transcript source id missing. jobId={}, step=source-id, stdoutLog={}, stderrLog={}",
                    jobId, result.stdoutLog(), result.stderrLog());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "영상 ID를 확인하지 못했습니다.");
        }

        log.info("transcript job step succeeded. jobId={}, step=source-id, sourceId={}", jobId, sourceId);
        return sourceId;
    }

    private Path downloadAudio(String jobId, String url, Path jobDir) throws IOException {
        log.info("transcript job step started. jobId={}, step=download-audio", jobId);
        ProcessResult result = runExternalCommand(
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
            Path sourceAudio = files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(".part"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
            if (sourceAudio == null) {
                log.error("transcript downloaded audio missing. jobId={}, jobDir={}, stdoutLog={}, stderrLog={}",
                        jobId, jobDir, result.stdoutLog(), result.stderrLog());
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "영상 오디오 다운로드 파일을 찾지 못했습니다.");
            }

            log.info("transcript job step succeeded. jobId={}, step=download-audio, audioFile={}", jobId, sourceAudio);
            return sourceAudio;
        } catch (IOException e) {
            log.error("transcript downloaded audio lookup failed. jobId={}, jobDir={}", jobId, jobDir, e);
            throw e;
        }
    }

    private Path convertToWhisperWav(String jobId, Path sourceAudio, Path jobDir) {
        log.info("transcript job step started. jobId={}, step=ffmpeg-wav, sourceAudio={}", jobId, sourceAudio);
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
        log.info("transcript job step succeeded. jobId={}, step=ffmpeg-wav, wavAudio={}", jobId, wavAudio);
        return wavAudio;
    }

    private Path runWhisper(String jobId, Path wavAudio, Path outputDir) {
        log.info("transcript job step started. jobId={}, step=whisper, wavAudio={}", jobId, wavAudio);
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
            log.error("transcript output file missing. jobId={}, expectedFile={}", jobId, transcriptFile);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "whisper.cpp 결과 txt 파일을 찾지 못했습니다.");
        }
        log.info("transcript job step succeeded. jobId={}, step=whisper, transcriptFile={}", jobId, transcriptFile);
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
            log.error("transcript external command failed. jobId={}, step={}, exitCode={}, stdoutLog={}, stderrLog={}, message={}",
                    jobId, step, e.getExitCode(), e.getStdoutLog(), e.getStderrLog(), userMessage, e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, userMessage);
        }
    }

    private TranscriptResponseDto toResponse(TranscriptJob job, boolean includeTranscript) {
        String transcript = null;
        if (includeTranscript && job.status == TranscriptJobStatus.SUCCEEDED && job.outputFile != null) {
            transcript = readTranscript(job);
        }

        return TranscriptResponseDto.builder()
                .jobId(job.jobId)
                .status(job.status)
                .sourceId(job.sourceId)
                .transcript(transcript)
                .outputFile(job.outputFile)
                .elapsedSeconds(elapsedSeconds(job))
                .errorMessage(job.errorMessage)
                .build();
    }

    private String readTranscript(TranscriptJob job) {
        Path outputFile = Path.of(job.outputFile);
        try {
            return Files.readString(outputFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("transcript output file read failed. jobId={}, outputFile={}", job.jobId, outputFile, e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "스크립트 결과 파일을 읽지 못했습니다.");
        }
    }

    private Long elapsedSeconds(TranscriptJob job) {
        Instant start = job.startedAt != null ? job.startedAt : job.createdAt;
        Instant end = job.finishedAt != null ? job.finishedAt : Instant.now();
        return Duration.between(start, end).toSeconds();
    }

    private void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minus(JOB_RETENTION);
        jobs.entrySet().removeIf(entry -> {
            TranscriptJob job = entry.getValue();
            return job.finishedAt != null && job.finishedAt.isBefore(cutoff);
        });
    }

    private String createJobId() {
        String timestamp = LocalDateTime.now().format(JOB_TIME_FORMATTER);
        String random = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "-" + random;
    }

    private static class TranscriptJob {

        private final String jobId;
        private final String url;
        private final Instant createdAt;
        private volatile TranscriptJobStatus status = TranscriptJobStatus.PENDING;
        private volatile Instant startedAt;
        private volatile Instant finishedAt;
        private volatile String sourceId;
        private volatile String outputFile;
        private volatile String errorMessage;

        private TranscriptJob(String jobId, String url, Instant createdAt) {
            this.jobId = jobId;
            this.url = url;
            this.createdAt = createdAt;
        }
    }
}
