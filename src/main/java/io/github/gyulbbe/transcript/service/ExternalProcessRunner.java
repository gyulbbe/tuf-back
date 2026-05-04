package io.github.gyulbbe.transcript.service;

import io.github.gyulbbe.transcript.config.TranscriptProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalProcessRunner {

    private static final int LOG_SNIPPET_BYTES = 32_000;
    private static final Duration FORCE_DESTROY_TIMEOUT = Duration.ofSeconds(5);

    private final TranscriptProperties properties;

    public ProcessResult run(
            String jobId,
            String step,
            List<String> command,
            Path workingDirectory,
            Duration timeout
    ) {
        Path logDir = Path.of(properties.getLogDir());
        Path stdoutLog = logDir.resolve(jobId + "-" + safeLogName(step) + ".stdout.log");
        Path stderrLog = logDir.resolve(jobId + "-" + safeLogName(step) + ".stderr.log");

        try {
            Files.createDirectories(logDir);
            if (workingDirectory != null) {
                Files.createDirectories(workingDirectory);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory.toFile());
            }
            processBuilder.redirectOutput(stdoutLog.toFile());
            processBuilder.redirectError(stderrLog.toFile());

            log.info("external command start. jobId={}, step={}, command={}", jobId, step, command);
            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(FORCE_DESTROY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
                throw failure(jobId, step, -1, stdoutLog, stderrLog, "external command timed out");
            }

            int exitCode = process.exitValue();
            ProcessResult result = new ProcessResult(
                    exitCode,
                    readLogSnippet(stdoutLog),
                    readLogSnippet(stderrLog),
                    stdoutLog,
                    stderrLog
            );

            if (exitCode != 0) {
                throw failure(jobId, step, exitCode, stdoutLog, stderrLog, "external command failed");
            }

            log.info("external command success. jobId={}, step={}, stdoutLog={}, stderrLog={}",
                    jobId, step, stdoutLog, stderrLog);
            return result;
        } catch (IOException e) {
            throw new ExternalProcessException("external command could not start", e, -1, stdoutLog, stderrLog);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalProcessException("external command interrupted", e, -1, stdoutLog, stderrLog);
        }
    }

    private ExternalProcessException failure(
            String jobId,
            String step,
            int exitCode,
            Path stdoutLog,
            Path stderrLog,
            String message
    ) {
        String stdout = readLogSnippet(stdoutLog);
        String stderr = readLogSnippet(stderrLog);
        log.error("{} jobId={}, step={}, exitCode={}, stdoutLog={}, stderrLog={}, stdout={}, stderr={}",
                message, jobId, step, exitCode, stdoutLog, stderrLog, stdout, stderr);
        return new ExternalProcessException(message, exitCode, stdoutLog, stderrLog);
    }

    private String readLogSnippet(Path logFile) {
        try {
            if (!Files.exists(logFile)) {
                return "";
            }

            long size = Files.size(logFile);
            if (size <= LOG_SNIPPET_BYTES) {
                return Files.readString(logFile, StandardCharsets.UTF_8);
            }

            byte[] buffer = new byte[LOG_SNIPPET_BYTES];
            try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
                file.seek(size - LOG_SNIPPET_BYTES);
                file.readFully(buffer);
            }
            return "...(log truncated)\n" + new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("external command log read failed. logFile={}", logFile, e);
            return "";
        }
    }

    private String safeLogName(String step) {
        return step.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record ProcessResult(
            int exitCode,
            String stdout,
            String stderr,
            Path stdoutLog,
            Path stderrLog
    ) {
    }

    @Getter
    public static class ExternalProcessException extends RuntimeException {

        private final int exitCode;
        private final Path stdoutLog;
        private final Path stderrLog;

        public ExternalProcessException(String message, int exitCode, Path stdoutLog, Path stderrLog) {
            super(message);
            this.exitCode = exitCode;
            this.stdoutLog = stdoutLog;
            this.stderrLog = stderrLog;
        }

        public ExternalProcessException(String message, Throwable cause, int exitCode, Path stdoutLog, Path stderrLog) {
            super(message, cause);
            this.exitCode = exitCode;
            this.stdoutLog = stdoutLog;
            this.stderrLog = stderrLog;
        }
    }
}
