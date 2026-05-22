package io.github.gyulbbe.chat.dto;

public record AiChatSettingsSnapshot(
        AiChatRoutingMode routingMode,
        String cloudflareModel,
        String ollamaModel
) {
}
