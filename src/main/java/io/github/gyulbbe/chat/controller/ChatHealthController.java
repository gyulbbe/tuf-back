package io.github.gyulbbe.chat.controller;

import io.github.gyulbbe.chat.provider.ChatProviderRouter;
import io.github.gyulbbe.chat.provider.ChatProviderStatus;
import io.github.gyulbbe.common.dto.ResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatHealthController {

    private final ChatProviderRouter chatProviderRouter;

    @GetMapping("/chat/health")
    public ResponseEntity<ResponseDto<ChatProviderStatus>> health() {
        return ResponseEntity.ok(ResponseDto.success(chatProviderRouter.status()));
    }
}