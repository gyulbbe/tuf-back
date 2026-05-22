package io.github.gyulbbe.chat.service;

import io.github.gyulbbe.chat.dto.AiChatRoutingMode;
import io.github.gyulbbe.chat.dto.AiChatSettingsRequestDto;
import io.github.gyulbbe.chat.dto.AiChatSettingsResponseDto;
import io.github.gyulbbe.chat.dto.AiChatSettingsSnapshot;
import io.github.gyulbbe.chat.dto.AiChatTestProvider;
import io.github.gyulbbe.chat.dto.AiChatTestRequestDto;
import io.github.gyulbbe.chat.dto.AiChatTestResponseDto;
import io.github.gyulbbe.chat.entity.AiChatSettingsEntity;
import io.github.gyulbbe.chat.provider.CloudflareChatProvider;
import io.github.gyulbbe.chat.provider.OllamaChatProvider;
import io.github.gyulbbe.chat.repository.AiChatSettingsRepository;
import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.common.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatSettingsService {

    private static final String TEST_SYSTEM_PROMPT = "You are a concise Korean assistant. Answer only in Korean.";
    private static final int MAX_MODEL_LENGTH = 255;

    private final AiChatSettingsRepository aiChatSettingsRepository;
    private final CloudflareChatProvider cloudflareChatProvider;
    private final OllamaChatProvider ollamaChatProvider;

    @Value("${cloudflare.ai.model:@cf/google/gemma-4-26b-a4b-it}")
    private String defaultCloudflareModel;

    @Value("${spring.ai.ollama.chat.options.model:gemma4:e4b}")
    private String defaultOllamaModel;

    @Transactional(readOnly = true)
    public ResponseDto<AiChatSettingsResponseDto> getSettings() {
        return ResponseDto.success(toResponse(loadEffectiveEntityOrDefaults()));
    }

    @Transactional(readOnly = true)
    public AiChatSettingsSnapshot currentSettings() {
        AiChatSettingsResponseDto settings = toResponse(loadEffectiveEntityOrDefaults());
        return new AiChatSettingsSnapshot(
                settings.getRoutingMode(),
                settings.getCloudflareModel(),
                settings.getOllamaModel()
        );
    }

    @Transactional
    public ResponseDto<AiChatSettingsResponseDto> updateSettings(
            AiChatSettingsRequestDto request,
            Long updatedBy
    ) {
        try {
            NormalizedSettings normalized = normalizeAndValidate(request);
            AiChatSettingsEntity entity = aiChatSettingsRepository.findById(AiChatSettingsEntity.DEFAULT_KEY)
                    .orElseGet(() -> AiChatSettingsEntity.create(
                            normalized.routingMode(),
                            normalized.cloudflareModel(),
                            normalized.ollamaModel(),
                            updatedBy
                    ));
            entity.update(
                    normalized.routingMode(),
                    normalized.cloudflareModel(),
                    normalized.ollamaModel(),
                    updatedBy
            );
            AiChatSettingsEntity saved = aiChatSettingsRepository.saveAndFlush(entity);
            return ResponseDto.success(toResponse(saved));
        } catch (IllegalArgumentException e) {
            return validationFailed(e.getMessage());
        } catch (Exception e) {
            markRollbackOnly();
            log.warn("Failed to update AI chat settings.", e);
            return ResponseDto.fail("Failed to update AI chat settings.");
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<AiChatTestResponseDto> testProvider(AiChatTestRequestDto request) {
        try {
            if (request == null || request.getProvider() == null) {
                throw new IllegalArgumentException("provider is required.");
            }
            String message = normalizeRequired(request.getMessage(), "message");
            AiChatSettingsSnapshot settings = currentSettings();

            if (request.getProvider() == AiChatTestProvider.CLOUDFLARE) {
                if (!cloudflareChatProvider.isConfigured(settings.cloudflareModel())) {
                    throw new IllegalArgumentException("Cloudflare AI is not configured.");
                }
                String response = cloudflareChatProvider.chat(TEST_SYSTEM_PROMPT, message, settings.cloudflareModel());
                return ResponseDto.success(AiChatTestResponseDto.builder()
                        .provider(AiChatTestProvider.CLOUDFLARE)
                        .model(settings.cloudflareModel())
                        .response(response)
                        .build());
            }

            String response = ollamaChatProvider.chat(TEST_SYSTEM_PROMPT, message, settings.ollamaModel());
            return ResponseDto.success(AiChatTestResponseDto.builder()
                    .provider(AiChatTestProvider.OLLAMA)
                    .model(settings.ollamaModel())
                    .response(response)
                    .build());
        } catch (IllegalArgumentException e) {
            return validationFailed(e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to test AI chat provider. provider={}", request == null ? null : request.getProvider(), e);
            return ResponseDto.fail("Failed to test AI chat provider: " + e.getMessage());
        }
    }

    private AiChatSettingsEntity loadEffectiveEntityOrDefaults() {
        return aiChatSettingsRepository.findById(AiChatSettingsEntity.DEFAULT_KEY)
                .orElseGet(() -> AiChatSettingsEntity.create(
                        AiChatRoutingMode.AUTO,
                        normalizeDefault(defaultCloudflareModel, "@cf/google/gemma-4-26b-a4b-it"),
                        normalizeDefault(defaultOllamaModel, "gemma4:e4b"),
                        null
                ));
    }

    private AiChatSettingsResponseDto toResponse(AiChatSettingsEntity entity) {
        return AiChatSettingsResponseDto.builder()
                .routingMode(entity.getRoutingMode() == null ? AiChatRoutingMode.AUTO : entity.getRoutingMode())
                .cloudflareModel(normalizeDefault(entity.getCloudflareModel(), normalizeDefault(defaultCloudflareModel, "@cf/google/gemma-4-26b-a4b-it")))
                .ollamaModel(normalizeDefault(entity.getOllamaModel(), normalizeDefault(defaultOllamaModel, "gemma4:e4b")))
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private NormalizedSettings normalizeAndValidate(AiChatSettingsRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required.");
        }
        AiChatRoutingMode routingMode = request.getRoutingMode();
        if (routingMode == null) {
            throw new IllegalArgumentException("routingMode is required.");
        }
        String cloudflareModel = normalizeRequired(request.getCloudflareModel(), "cloudflareModel");
        String ollamaModel = normalizeRequired(request.getOllamaModel(), "ollamaModel");
        return new NormalizedSettings(routingMode, cloudflareModel, ollamaModel);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_MODEL_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be 255 characters or less.");
        }
        return normalized;
    }

    private String normalizeDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private <T> ResponseDto<T> validationFailed(String message) {
        return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, message, ApiErrorCode.VALIDATION_FAILED);
    }

    private void markRollbackOnly() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }

    private record NormalizedSettings(
            AiChatRoutingMode routingMode,
            String cloudflareModel,
            String ollamaModel
    ) {
    }
}
