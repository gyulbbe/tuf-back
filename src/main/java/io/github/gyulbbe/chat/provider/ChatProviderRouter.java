package io.github.gyulbbe.chat.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatProviderRouter {

    /**
     * Cloudflare Workers AI 일일 한도는 매일 UTC 00:00 에 초기화된다.
     * (공식 문서: "All limits reset daily at 00:00 UTC.")
     */
    private static final int PROBE_MAX_ATTEMPTS = 5;
    private static final Duration PROBE_RETRY_INTERVAL = Duration.ofMinutes(3);

    private final CloudflareChatProvider cloudflareProvider;
    private final OllamaChatProvider ollamaProvider;

    private volatile Instant cloudflareResumeAt = Instant.EPOCH;
    private volatile Instant probeNextAttemptAt = Instant.EPOCH;
    private volatile int probeAttemptsRemaining = 0;

    public String chat(String systemPrompt, String userMessage) {
        if (canAttemptCloudflare(Instant.now())) {
            try {
                String response = cloudflareProvider.chat(systemPrompt, userMessage);
                onCloudflareSuccess();
                log.info("Chat served by Cloudflare Workers AI");
                return response;
            } catch (CloudflareQuotaExhaustedException _) {
                onCloudflareQuotaExhausted(Instant.now());
            } catch (Exception e) {
                log.warn("Cloudflare 호출 실패({}). 이번 요청만 Ollama로 전환", e.getMessage());
            }
        }

        log.info("Chat served by local Ollama");
        return ollamaProvider.chat(systemPrompt, userMessage);
    }

    /** health 엔드포인트용 현재 상태 스냅샷. */
    public synchronized ChatProviderStatus status() {
        Instant now = Instant.now();
        maybeEnterProbeMode(now);
        String active = canAttemptCloudflare(now) ? "cloudflare" : "ollama";
        Instant blockedUntil = cloudflareResumeAt.equals(Instant.EPOCH) ? null : cloudflareResumeAt;
        return new ChatProviderStatus(
                active,
                cloudflareProvider.isConfigured(),
                blockedUntil,
                probeAttemptsRemaining,
                probeNextAttemptAt.equals(Instant.EPOCH) ? null : probeNextAttemptAt
        );
    }

    private synchronized boolean canAttemptCloudflare(Instant now) {
        if (!cloudflareProvider.isConfigured()) {
            return false;
        }
        maybeEnterProbeMode(now);
        if (now.isBefore(cloudflareResumeAt)) {
            return false;
        }
        return !now.isBefore(probeNextAttemptAt);
    }

    /** 하드 블록 시간이 지나면 자동으로 probe 모드에 진입해 재시도 횟수를 채운다. */
    private void maybeEnterProbeMode(Instant now) {
        if (cloudflareResumeAt.isAfter(Instant.EPOCH)
                && now.isAfter(cloudflareResumeAt)
                && probeAttemptsRemaining == 0
                && probeNextAttemptAt.equals(Instant.EPOCH)) {
            probeAttemptsRemaining = PROBE_MAX_ATTEMPTS;
            log.info("Cloudflare 리셋 시각 경과. probe 모드 진입 (재시도 {}회)", PROBE_MAX_ATTEMPTS);
        }
    }

    private synchronized void onCloudflareSuccess() {
        cloudflareResumeAt = Instant.EPOCH;
        probeNextAttemptAt = Instant.EPOCH;
        probeAttemptsRemaining = 0;
    }

    private synchronized void onCloudflareQuotaExhausted(Instant now) {
        if (probeAttemptsRemaining > 0) {
            probeAttemptsRemaining--;
            if (probeAttemptsRemaining == 0) {
                cloudflareResumeAt = nextUtcMidnight(now);
                probeNextAttemptAt = Instant.EPOCH;
                log.warn("Cloudflare probe 재시도 모두 실패. {}까지 Ollama 사용", cloudflareResumeAt);
            } else {
                probeNextAttemptAt = now.plus(PROBE_RETRY_INTERVAL);
                log.warn("Cloudflare 아직 할당량 미복구 (남은 재시도 {}회, 다음 시도 {})",
                        probeAttemptsRemaining, probeNextAttemptAt);
            }
        } else {
            cloudflareResumeAt = nextUtcMidnight(now);
            probeNextAttemptAt = Instant.EPOCH;
            log.warn("Cloudflare 일일 한도 소진. {}까지 Ollama로 전환", cloudflareResumeAt);
        }
    }

    private Instant nextUtcMidnight(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }
}