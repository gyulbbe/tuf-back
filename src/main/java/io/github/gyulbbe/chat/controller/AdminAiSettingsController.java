package io.github.gyulbbe.chat.controller;

import io.github.gyulbbe.chat.dto.AiChatSettingsRequestDto;
import io.github.gyulbbe.chat.dto.AiChatSettingsResponseDto;
import io.github.gyulbbe.chat.dto.AiChatTestRequestDto;
import io.github.gyulbbe.chat.dto.AiChatTestResponseDto;
import io.github.gyulbbe.chat.service.AiChatSettingsService;
import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai-settings")
public class AdminAiSettingsController {

    private final AiChatSettingsService aiChatSettingsService;

    @GetMapping
    public ResponseEntity<ResponseDto<AiChatSettingsResponseDto>> getSettings() {
        return respond(aiChatSettingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<ResponseDto<AiChatSettingsResponseDto>> updateSettings(
            @RequestBody AiChatSettingsRequestDto request,
            Authentication authentication
    ) {
        return respond(aiChatSettingsService.updateSettings(request, resolveUserPk(authentication)));
    }

    @PostMapping("/test")
    public ResponseEntity<ResponseDto<AiChatTestResponseDto>> testProvider(@RequestBody AiChatTestRequestDto request) {
        return respond(aiChatSettingsService.testProvider(request));
    }

    private Long resolveUserPk(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails customUserDetails)) {
            return null;
        }
        return customUserDetails.getUserPk();
    }
}
