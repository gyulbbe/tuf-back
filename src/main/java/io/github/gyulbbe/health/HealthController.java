package io.github.gyulbbe.health;

import io.github.gyulbbe.chat.provider.ChatProviderRouter;
import io.github.gyulbbe.common.utils.embeddingVector.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final ChatProviderRouter chatProviderRouter;
    private final EmbeddingService embeddingService;

    @Value("${app.git-commit:unknown}")
    private String gitCommit;

    @Value("${app.git-branch:unknown}")
    private String gitBranch;

    @Value("${app.build-time:unknown}")
    private String buildTime;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("commit", gitCommit);
        info.put("branch", gitBranch);
        info.put("buildTime", buildTime);
        info.put("chat", chatProviderRouter.status());
        info.put("embedding", embeddingHealth());
        return ResponseEntity.ok(info);
    }

    private EmbeddingHealthStatus embeddingHealth() {
        try {
            float[] testVector = embeddingService.getEmbedding("테스트");
            return EmbeddingHealthStatus.up(testVector.length);
        } catch (Exception e) {
            log.error("임베딩 API 헬스체크 실패", e);
            return EmbeddingHealthStatus.down(e.getMessage());
        }
    }
}