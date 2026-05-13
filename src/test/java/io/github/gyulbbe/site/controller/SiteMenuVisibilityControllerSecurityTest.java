package io.github.gyulbbe.site.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.SecurityConfig;
import io.github.gyulbbe.jwt.JWTUtil;
import io.github.gyulbbe.site.dto.MenuVisibilityItemDto;
import io.github.gyulbbe.site.dto.MenuVisibilityResponseDto;
import io.github.gyulbbe.site.service.SiteMenuVisibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SiteMenuVisibilityController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "tuf-front.url=http://localhost",
        "spring.jwt.secret=12345678901234567890123456789012"
})
class SiteMenuVisibilityControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SiteMenuVisibilityService siteMenuVisibilityService;

    @MockBean
    private AuthenticationConfiguration authenticationConfiguration;

    @MockBean
    private JWTUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(siteMenuVisibilityService.getMenuVisibility()).thenReturn(ResponseDto.success(responseDto()));
        when(siteMenuVisibilityService.updateMenuVisibility(any(), nullable(Long.class)))
                .thenReturn(ResponseDto.success(responseDto()));
    }

    @Test
    void getMenuVisibility_allowsAnonymousUsers() throws Exception {
        mockMvc.perform(get("/site/menu-visibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.items[0].menuKey").value("chat"))
                .andExpect(jsonPath("$.data.items[0].visible").value(true));
    }

    @Test
    void updateMenuVisibility_returnsUnauthorizedWhenAuthenticationMissing() throws Exception {
        mockMvc.perform(put("/admin/menu-visibility")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void updateMenuVisibility_returnsForbiddenForRoleUser() throws Exception {
        mockMvc.perform(put("/admin/menu-visibility")
                        .with(user("user").roles("USER"))
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));
    }

    @Test
    void updateMenuVisibility_allowsRoleAdmin() throws Exception {
        assertUpdateAllowedForRole("ADMIN");
    }

    @Test
    void updateMenuVisibility_allowsRoleManager() throws Exception {
        assertUpdateAllowedForRole("MANAGER");
    }

    @Test
    void updateMenuVisibility_allowsRoleMaster() throws Exception {
        assertUpdateAllowedForRole("MASTER");
    }

    private void assertUpdateAllowedForRole(String role) throws Exception {
        mockMvc.perform(put("/admin/menu-visibility")
                        .with(user("admin").roles(role))
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.items[0].menuKey").value("chat"));
    }

    private MenuVisibilityResponseDto responseDto() {
        MenuVisibilityResponseDto responseDto = new MenuVisibilityResponseDto();
        responseDto.setItems(List.of(new MenuVisibilityItemDto("chat", true)));
        return responseDto;
    }

    private String requestJson() {
        return """
                {
                  "items": [
                    {
                      "menuKey": "chat",
                      "visible": true
                    }
                  ]
                }
                """;
    }
}
