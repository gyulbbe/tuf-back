package io.github.gyulbbe.health;

import io.github.gyulbbe.chat.provider.ChatProviderRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final ChatProviderRouter chatProviderRouter;

    @Value("${app.git-commit}")
    private String gitCommit;

    @Value("${app.git-branch}")
    private String gitBranch;

    @Value("${app.build-time}")
    private String buildTime;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("commit", gitCommit);
        info.put("branch", gitBranch);
        info.put("buildTime", buildTime);
        info.put("chat", chatProviderRouter.status());
        return ResponseEntity.ok(info);
    }
}