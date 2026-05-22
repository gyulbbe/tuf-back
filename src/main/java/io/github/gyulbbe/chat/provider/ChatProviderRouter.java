package io.github.gyulbbe.chat.provider;

import io.github.gyulbbe.chat.dto.AiChatRoutingMode;
import io.github.gyulbbe.chat.dto.AiChatSettingsSnapshot;
import io.github.gyulbbe.chat.service.AiChatSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@Component
public class ChatProviderRouter {

    /**
     * Cloudflare Workers AI daily limits reset at UTC 00:00.
     */
    private static final int PROBE_MAX_ATTEMPTS = 5;
    private static final Duration PROBE_RETRY_INTERVAL = Duration.ofMinutes(3);

    private final CloudflareChatProvider cloudflareProvider;
    private final OllamaChatProvider ollamaProvider;
    private final AiChatSettingsService aiChatSettingsService;

    private volatile Instant cloudflareResumeAt = Instant.EPOCH;
    private volatile Instant probeNextAttemptAt = Instant.EPOCH;
    private volatile int probeAttemptsRemaining = 0;

    public ChatProviderRouter(
            CloudflareChatProvider cloudflareProvider,
            OllamaChatProvider ollamaProvider,
            AiChatSettingsService aiChatSettingsService
    ) {
        this.cloudflareProvider = cloudflareProvider;
        this.ollamaProvider = ollamaProvider;
        this.aiChatSettingsService = aiChatSettingsService;
    }

    public String chat(String systemPrompt, String userMessage) {
        AiChatSettingsSnapshot settings = aiChatSettingsService.currentSettings();

        if (settings.routingMode() == AiChatRoutingMode.OLLAMA_ONLY) {
            return chatWithOllama(systemPrompt, userMessage, settings.ollamaModel());
        }

        if (settings.routingMode() == AiChatRoutingMode.CLOUDFLARE_ONLY) {
            if (!cloudflareProvider.isConfigured(settings.cloudflareModel())) {
                throw new IllegalStateException("Cloudflare AI is not configured.");
            }
            String response = cloudflareProvider.chat(systemPrompt, userMessage, settings.cloudflareModel());
            onCloudflareSuccess();
            log.info("Chat served by Cloudflare Workers AI (forced)");
            return response;
        }

        if (canAttemptCloudflare(Instant.now(), settings.cloudflareModel())) {
            try {
                String response = cloudflareProvider.chat(systemPrompt, userMessage, settings.cloudflareModel());
                onCloudflareSuccess();
                log.info("Chat served by Cloudflare Workers AI");
                return response;
            } catch (CloudflareQuotaExhaustedException _) {
                onCloudflareQuotaExhausted(Instant.now());
            } catch (Exception e) {
                log.warn("Cloudflare call failed ({}). Falling back to Ollama for this request.", e.getMessage());
            }
        }

        return chatWithOllama(systemPrompt, userMessage, settings.ollamaModel());
    }

    public synchronized ChatProviderStatus status() {
        Instant now = Instant.now();
        AiChatSettingsSnapshot settings = aiChatSettingsService.currentSettings();
        maybeEnterProbeMode(now);
        String active = activeProvider(now, settings);
        Instant blockedUntil = cloudflareResumeAt.equals(Instant.EPOCH) ? null : cloudflareResumeAt;
        return new ChatProviderStatus(
                active,
                cloudflareProvider.isConfigured(settings.cloudflareModel()),
                blockedUntil,
                probeAttemptsRemaining,
                probeNextAttemptAt.equals(Instant.EPOCH) ? null : probeNextAttemptAt
        );
    }

    private String chatWithOllama(String systemPrompt, String userMessage, String model) {
        log.info("Chat served by local Ollama");
        return ollamaProvider.chat(systemPrompt, userMessage, model);
    }

    private String activeProvider(Instant now, AiChatSettingsSnapshot settings) {
        if (settings.routingMode() == AiChatRoutingMode.OLLAMA_ONLY) {
            return "ollama";
        }
        if (settings.routingMode() == AiChatRoutingMode.CLOUDFLARE_ONLY) {
            return "cloudflare";
        }
        return canAttemptCloudflare(now, settings.cloudflareModel()) ? "cloudflare" : "ollama";
    }

    private synchronized boolean canAttemptCloudflare(Instant now, String cloudflareModel) {
        if (!cloudflareProvider.isConfigured(cloudflareModel)) {
            return false;
        }
        maybeEnterProbeMode(now);
        if (now.isBefore(cloudflareResumeAt)) {
            return false;
        }
        return !now.isBefore(probeNextAttemptAt);
    }

    private void maybeEnterProbeMode(Instant now) {
        if (cloudflareResumeAt.isAfter(Instant.EPOCH)
                && now.isAfter(cloudflareResumeAt)
                && probeAttemptsRemaining == 0
                && probeNextAttemptAt.equals(Instant.EPOCH)) {
            probeAttemptsRemaining = PROBE_MAX_ATTEMPTS;
            log.info("Cloudflare resume time passed. Entering probe mode ({} attempts).", PROBE_MAX_ATTEMPTS);
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
                log.warn("All Cloudflare probe attempts failed. Using Ollama until {}", cloudflareResumeAt);
            } else {
                probeNextAttemptAt = now.plus(PROBE_RETRY_INTERVAL);
                log.warn("Cloudflare quota still unavailable ({} probe attempts left). Next attempt at {}",
                        probeAttemptsRemaining, probeNextAttemptAt);
            }
        } else {
            cloudflareResumeAt = nextUtcMidnight(now);
            probeNextAttemptAt = Instant.EPOCH;
            log.warn("Cloudflare daily quota exhausted. Falling back to Ollama until {}", cloudflareResumeAt);
        }
    }

    private Instant nextUtcMidnight(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }
}
