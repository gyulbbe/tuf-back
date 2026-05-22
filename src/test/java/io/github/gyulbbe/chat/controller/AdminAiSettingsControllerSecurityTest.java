package io.github.gyulbbe.chat.controller;

import io.github.gyulbbe.chat.dto.AiChatRoutingMode;
import io.github.gyulbbe.chat.dto.AiChatSettingsResponseDto;
import io.github.gyulbbe.chat.dto.AiChatTestProvider;
import io.github.gyulbbe.chat.dto.AiChatTestResponseDto;
import io.github.gyulbbe.chat.service.AiChatSettingsService;
import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.SecurityConfig;
import io.github.gyulbbe.jwt.JWTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAiSettingsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "tuf-front.url=http://localhost",
        "spring.jwt.secret=12345678901234567890123456789012"
})
class AdminAiSettingsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiChatSettingsService aiChatSettingsService;

    @MockBean
    private AuthenticationConfiguration authenticationConfiguration;

    @MockBean
    private JWTUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(aiChatSettingsService.getSettings()).thenReturn(ResponseDto.success(settingsResponse()));
        when(aiChatSettingsService.updateSettings(any(), nullable(Long.class)))
                .thenReturn(ResponseDto.success(settingsResponse()));
        when(aiChatSettingsService.testProvider(any())).thenReturn(ResponseDto.success(testResponse()));
    }

    @Test
    void aiSettingsEndpoints_returnUnauthorizedWhenAuthenticationMissing() throws Exception {
        mockMvc.perform(get("/admin/ai-settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));

        mockMvc.perform(post("/admin/ai-settings/test")
                        .contentType("application/json")
                        .content(testJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void aiSettingsEndpoints_returnForbiddenForRoleUser() throws Exception {
        mockMvc.perform(get("/admin/ai-settings")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));

        mockMvc.perform(put("/admin/ai-settings")
                        .with(user("user").roles("USER"))
                        .contentType("application/json")
                        .content(settingsJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));

        mockMvc.perform(post("/admin/ai-settings/test")
                        .with(user("user").roles("USER"))
                        .contentType("application/json")
                        .content(testJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));
    }

    @Test
    void aiSettingsEndpoints_allowRoleAdmin() throws Exception {
        assertEndpointsAllowedForRole("ADMIN");
    }

    @Test
    void aiSettingsEndpoints_allowRoleManager() throws Exception {
        assertEndpointsAllowedForRole("MANAGER");
    }

    @Test
    void aiSettingsEndpoints_allowRoleMaster() throws Exception {
        assertEndpointsAllowedForRole("MASTER");
    }

    private void assertEndpointsAllowedForRole(String role) throws Exception {
        mockMvc.perform(get("/admin/ai-settings")
                        .with(user("admin").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.routingMode").value("AUTO"));

        mockMvc.perform(put("/admin/ai-settings")
                        .with(user("admin").roles(role))
                        .contentType("application/json")
                        .content(settingsJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.cloudflareModel").value("@cf/test/cloudflare"));

        mockMvc.perform(post("/admin/ai-settings/test")
                        .with(user("admin").roles(role))
                        .contentType("application/json")
                        .content(testJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.provider").value("CLOUDFLARE"));
    }

    private AiChatSettingsResponseDto settingsResponse() {
        return AiChatSettingsResponseDto.builder()
                .routingMode(AiChatRoutingMode.AUTO)
                .cloudflareModel("@cf/test/cloudflare")
                .ollamaModel("gemma4:e4b")
                .updatedBy(1L)
                .updatedAt(LocalDateTime.of(2026, 5, 22, 10, 0))
                .build();
    }

    private AiChatTestResponseDto testResponse() {
        return AiChatTestResponseDto.builder()
                .provider(AiChatTestProvider.CLOUDFLARE)
                .model("@cf/test/cloudflare")
                .response("ok")
                .build();
    }

    private String settingsJson() {
        return """
                {
                  "routingMode": "AUTO",
                  "cloudflareModel": "@cf/test/cloudflare",
                  "ollamaModel": "gemma4:e4b"
                }
                """;
    }

    private String testJson() {
        return """
                {
                  "provider": "CLOUDFLARE",
                  "message": "hello"
                }
                """;
    }
}
