package io.github.gyulbbe.chat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AiChatSettingsResponseDto {
    private AiChatRoutingMode routingMode;
    private String cloudflareModel;
    private String ollamaModel;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
