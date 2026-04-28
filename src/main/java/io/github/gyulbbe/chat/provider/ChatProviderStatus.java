package io.github.gyulbbe.chat.provider;

import java.time.Instant;

public record ChatProviderStatus(
        String activeProvider,
        boolean cloudflareConfigured,
        Instant cloudflareBlockedUntil,
        int probeAttemptsRemaining,
        Instant probeNextAttemptAt
) {
}