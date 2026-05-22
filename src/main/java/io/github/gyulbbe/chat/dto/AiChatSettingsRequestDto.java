package io.github.gyulbbe.chat.dto;

import lombok.Data;

@Data
public class AiChatSettingsRequestDto {
    private AiChatRoutingMode routingMode;
    private String cloudflareModel;
    private String ollamaModel;
}
