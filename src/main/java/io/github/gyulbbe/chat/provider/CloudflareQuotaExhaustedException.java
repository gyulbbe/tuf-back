package io.github.gyulbbe.chat.provider;

public class CloudflareQuotaExhaustedException extends RuntimeException {

    public CloudflareQuotaExhaustedException(String message) {
        super(message);
    }

    public CloudflareQuotaExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}