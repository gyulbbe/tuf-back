package io.github.gyulbbe.chat.entity;

import io.github.gyulbbe.chat.dto.AiChatRoutingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "AI_CHAT_SETTINGS")
public class AiChatSettingsEntity {

    public static final String DEFAULT_KEY = "default";

    @Id
    @Column(name = "SETTING_KEY", nullable = false, length = 50)
    private String settingKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROUTING_MODE", nullable = false, length = 30)
    private AiChatRoutingMode routingMode;

    @Column(name = "CLOUDFLARE_MODEL", nullable = false, length = 255)
    private String cloudflareModel;

    @Column(name = "OLLAMA_MODEL", nullable = false, length = 255)
    private String ollamaModel;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private AiChatSettingsEntity(
            String settingKey,
            AiChatRoutingMode routingMode,
            String cloudflareModel,
            String ollamaModel,
            Long updatedBy
    ) {
        this.settingKey = settingKey;
        this.routingMode = routingMode;
        this.cloudflareModel = cloudflareModel;
        this.ollamaModel = ollamaModel;
        this.updatedBy = updatedBy;
    }

    public static AiChatSettingsEntity create(
            AiChatRoutingMode routingMode,
            String cloudflareModel,
            String ollamaModel,
            Long updatedBy
    ) {
        return AiChatSettingsEntity.builder()
                .settingKey(DEFAULT_KEY)
                .routingMode(routingMode)
                .cloudflareModel(cloudflareModel)
                .ollamaModel(ollamaModel)
                .updatedBy(updatedBy)
                .build();
    }

    public void update(
            AiChatRoutingMode routingMode,
            String cloudflareModel,
            String ollamaModel,
            Long updatedBy
    ) {
        this.routingMode = routingMode;
        this.cloudflareModel = cloudflareModel;
        this.ollamaModel = ollamaModel;
        this.updatedBy = updatedBy;
    }

    @PrePersist
    void onCreate() {
        if (settingKey == null || settingKey.isBlank()) {
            settingKey = DEFAULT_KEY;
        }
        if (routingMode == null) {
            routingMode = AiChatRoutingMode.AUTO;
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
