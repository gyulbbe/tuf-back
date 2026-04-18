package io.github.gyulbbe.chat.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CloudflareChatProvider {

    private static final String API_URL_TEMPLATE =
            "https://api.cloudflare.com/client/v4/accounts/%s/ai/run/%s";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String accountId;
    private final String apiToken;
    private final String model;

    public CloudflareChatProvider(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${cloudflare.account-id:}") String accountId,
            @Value("${cloudflare.api-token:}") String apiToken,
            @Value("${cloudflare.ai.model:}") String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.accountId = accountId;
        this.apiToken = apiToken;
        this.model = model;
    }

    public boolean isConfigured() {
        return accountId != null && !accountId.isBlank()
                && apiToken != null && !apiToken.isBlank()
                && model != null && !model.isBlank();
    }

    public String chat(String systemPrompt, String userMessage) {
        String url = String.format(API_URL_TEMPLATE, accountId, model);

        Map<String, Object> body = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return parseResponse(response.getBody());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    || isQuotaError(e.getResponseBodyAsString())) {
                throw new CloudflareQuotaExhaustedException(
                        "Cloudflare quota exhausted: " + e.getStatusCode(), e);
            }
            throw new RuntimeException("Cloudflare AI call failed: " + e.getMessage(), e);
        } catch (CloudflareQuotaExhaustedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Cloudflare AI call failed: " + e.getMessage(), e);
        }
    }

    private String parseResponse(String body) throws Exception {
        JsonNode json = objectMapper.readTree(body);
        if (!json.path("success").asBoolean(false)) {
            String errors = json.path("errors").toString();
            if (isQuotaError(errors)) {
                throw new CloudflareQuotaExhaustedException("Cloudflare quota exhausted: " + errors);
            }
            throw new RuntimeException("Cloudflare AI error: " + errors);
        }

        JsonNode result = json.path("result");
        String response = result.path("response").asText("");
        if (response.isEmpty()) {
            // OpenAI 호환 포맷 (choices[0].message.content) 시도
            response = result.path("choices").path(0).path("message").path("content").asText("");
        }
        if (response.isEmpty()) {
            log.warn("Cloudflare 응답은 왔지만 텍스트를 찾지 못함. raw body: {}", body);
        }
        return response;
    }

    private boolean isQuotaError(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("quota") || lower.contains("rate limit")
                || lower.contains("exceed") || lower.contains("daily limit");
    }
}