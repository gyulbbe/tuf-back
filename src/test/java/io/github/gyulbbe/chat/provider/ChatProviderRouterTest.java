package io.github.gyulbbe.chat.provider;

import io.github.gyulbbe.chat.dto.AiChatRoutingMode;
import io.github.gyulbbe.chat.dto.AiChatSettingsSnapshot;
import io.github.gyulbbe.chat.service.AiChatSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatProviderRouterTest {

    private static final String SYSTEM_PROMPT = "system";
    private static final String USER_MESSAGE = "hello";
    private static final String CLOUDFLARE_MODEL = "@cf/test/cloudflare";
    private static final String OLLAMA_MODEL = "gemma4:e4b";

    @Mock
    private CloudflareChatProvider cloudflareProvider;

    @Mock
    private OllamaChatProvider ollamaProvider;

    @Mock
    private AiChatSettingsService aiChatSettingsService;

    private ChatProviderRouter router;

    @BeforeEach
    void setUp() {
        router = new ChatProviderRouter(cloudflareProvider, ollamaProvider, aiChatSettingsService);
    }

    @Test
    void autoMode_usesCloudflareResponseWhenCloudflareSucceeds() {
        givenSettings(AiChatRoutingMode.AUTO);
        when(cloudflareProvider.isConfigured(CLOUDFLARE_MODEL)).thenReturn(true);
        when(cloudflareProvider.chat(SYSTEM_PROMPT, USER_MESSAGE, CLOUDFLARE_MODEL)).thenReturn("cloudflare");

        String response = router.chat(SYSTEM_PROMPT, USER_MESSAGE);

        assertThat(response).isEqualTo("cloudflare");
        verify(cloudflareProvider).chat(SYSTEM_PROMPT, USER_MESSAGE, CLOUDFLARE_MODEL);
        verify(ollamaProvider, never()).chat(SYSTEM_PROMPT, USER_MESSAGE, OLLAMA_MODEL);
    }

    @Test
    void autoMode_fallsBackToOllamaWhenCloudflareQuotaIsExhausted() {
        givenSettings(AiChatRoutingMode.AUTO);
        when(cloudflareProvider.isConfigured(CLOUDFLARE_MODEL)).thenReturn(true);
        when(cloudflareProvider.chat(SYSTEM_PROMPT, USER_MESSAGE, CLOUDFLARE_MODEL))
                .thenThrow(new CloudflareQuotaExhaustedException("quota"));
        when(ollamaProvider.chat(SYSTEM_PROMPT, USER_MESSAGE, OLLAMA_MODEL)).thenReturn("ollama");

        String response = router.chat(SYSTEM_PROMPT, USER_MESSAGE);

        assertThat(response).isEqualTo("ollama");
        verify(cloudflareProvider).chat(SYSTEM_PROMPT, USER_MESSAGE, CLOUDFLARE_MODEL);
        verify(ollamaProvider).chat(SYSTEM_PROMPT, USER_MESSAGE, OLLAMA_MODEL);
    }

    @Test
    void cloudflareOnlyMode_doesNotFallBackWhenCloudflareFails() {
        givenSettings(AiChatRoutingMode.CLOUDFLARE_ONLY);
        when(cloudflareProvider.isConfigured(CLOUDFLARE_MODEL)).thenReturn(true);
        when(cloudflareProvider.chat(SYSTEM_PROMPT, USER_MESSAGE, CLOUDFLARE_MODEL))
                .thenThrow(new IllegalStateException("cloudflare failed"));

        assertThatThrownBy(() -> router.chat(SYSTEM_PROMPT, USER_MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cloudflare failed");

        verify(cloudflareProvider).chat(SYSTEM_PROMPT, USER_MESSAGE, CLOUDFLARE_MODEL);
        verifyNoInteractions(ollamaProvider);
    }

    @Test
    void ollamaOnlyMode_doesNotCallCloudflare() {
        givenSettings(AiChatRoutingMode.OLLAMA_ONLY);
        when(ollamaProvider.chat(SYSTEM_PROMPT, USER_MESSAGE, OLLAMA_MODEL)).thenReturn("ollama");

        String response = router.chat(SYSTEM_PROMPT, USER_MESSAGE);

        assertThat(response).isEqualTo("ollama");
        verifyNoInteractions(cloudflareProvider);
        verify(ollamaProvider).chat(SYSTEM_PROMPT, USER_MESSAGE, OLLAMA_MODEL);
    }

    @Test
    void autoMode_passesStoredModelNamesToProviders() {
        givenSettings(AiChatRoutingMode.AUTO);
        when(cloudflareProvider.isConfigured(CLOUDFLARE_MODEL)).thenReturn(false);
        when(ollamaProvider.chat(SYSTEM_PROMPT, USER_MESSAGE, OLLAMA_MODEL)).thenReturn("ollama");

        String response = router.chat(SYSTEM_PROMPT, USER_MESSAGE);

        assertThat(response).isEqualTo("ollama");
        verify(cloudflareProvider).isConfigured(CLOUDFLARE_MODEL);
        verify(ollamaProvider).chat(SYSTEM_PROMPT, USER_MESSAGE, OLLAMA_MODEL);
    }

    private void givenSettings(AiChatRoutingMode routingMode) {
        when(aiChatSettingsService.currentSettings())
                .thenReturn(new AiChatSettingsSnapshot(routingMode, CLOUDFLARE_MODEL, OLLAMA_MODEL));
    }
}
