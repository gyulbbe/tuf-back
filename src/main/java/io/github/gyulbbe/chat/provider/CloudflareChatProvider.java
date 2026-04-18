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

    private static final String DIRECT_API_URL_TEMPLATE =
            "https://api.cloudflare.com/client/v4/accounts/%s/ai/run/%s";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String accountId;
    private final String apiToken;
    private final String model;
    private final String gatewayBaseUrl;

    public CloudflareChatProvider(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${cloudflare.account-id:}") String accountId,
            @Value("${cloudflare.api-token:}") String apiToken,
            @Value("${cloudflare.ai.model:}") String model,
            @Value("${cloudflare.ai-gateway.base-url:}") String gatewayBaseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.accountId = accountId;
        this.apiToken = apiToken;
        this.model = model;
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    public boolean isConfigured() {
        boolean tokenAndModel = apiToken != null && !apiToken.isBlank()
                && model != null && !model.isBlank();
        if (!tokenAndModel) {
            return false;
        }
        return hasGateway() || (accountId != null && !accountId.isBlank());
    }

    public String chat(String systemPrompt, String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        String url;
        Map<String, Object> body;
        if (isCompatEndpoint()) {
            url = gatewayBaseUrl;
            body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    )
            );
        } else {
            url = String.format(DIRECT_API_URL_TEMPLATE, accountId, model);
            body = Map.of(
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    )
            );
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return isCompatEndpoint()
                    ? parseCompatResponse(response.getBody())
                    : parseNativeResponse(response.getBody());
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

    private boolean hasGateway() {
        return gatewayBaseUrl != null && !gatewayBaseUrl.isBlank();
    }

    private boolean isCompatEndpoint() {
        return hasGateway() && gatewayBaseUrl.contains("/chat/completions");
    }

    private String parseNativeResponse(String body) throws Exception {
        JsonNode json = objectMapper.readTree(body);
        if (!json.path("success").asBoolean(false)) {
            String errors = json.path("errors").toString();
            if (isQuotaError(errors)) {
                throw new CloudflareQuotaExhaustedException("Cloudflare quota exhausted: " + errors);
            }
            throw new RuntimeException("Cloudflare AI error: " + errors);
        }
        return json.path("result").path("response").asText();
    }

    private String parseCompatResponse(String body) throws Exception {
        JsonNode json = objectMapper.readTree(body);
        JsonNode error = json.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String message = error.path("message").asText();
            if (isQuotaError(message)) {
                throw new CloudflareQuotaExhaustedException("Cloudflare quota exhausted: " + message);
            }
            throw new RuntimeException("Cloudflare AI error: " + message);
        }
        return json.path("choices").path(0).path("message").path("content").asText();
    }

    private boolean isQuotaError(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("quota") || lower.contains("rate limit")
                || lower.contains("exceed") || lower.contains("daily limit")
                || lower.contains("insufficient_quota");
    }
}