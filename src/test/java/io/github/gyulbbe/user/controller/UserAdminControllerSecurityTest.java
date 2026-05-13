package io.github.gyulbbe.user.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.SecurityConfig;
import io.github.gyulbbe.jwt.JWTUtil;
import io.github.gyulbbe.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "tuf-front.url=http://localhost",
        "spring.jwt.secret=12345678901234567890123456789012"
})
class UserAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthenticationConfiguration authenticationConfiguration;

    @MockBean
    private JWTUtil jwtUtil;

    @Test
    void adminList_returnsForbidden_forRoleUser() throws Exception {
        mockMvc.perform(get("/user/admin/list").with(user("user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("권한이 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));
    }

    @Test
    void adminList_returnsUnauthorized_whenAuthenticationMissing() throws Exception {
        mockMvc.perform(get("/user/admin/list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void adminList_returnsOk_forRoleAdmin() throws Exception {
        when(userService.searchAdminUsers("", "ALL")).thenReturn(ResponseDto.success(List.of()));

        mockMvc.perform(get("/user/admin/list").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void createUser_usesResponseDtoStatusCode() throws Exception {
        when(userService.createAdminUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ResponseDto.fail(409, "이미 사용 중인 userId입니다."));

        mockMvc.perform(post("/user/admin")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": "existing",
                                  "password": "secret",
                                  "name": "Existing",
                                  "race": "P",
                                  "tier": "A"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 userId입니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }
    @Test
    void updateUserRole_returnsOk_forRoleAdmin() throws Exception {
        when(userService.updateAdminUserRole(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ResponseDto.success(null));

        mockMvc.perform(patch("/user/admin/10/role")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "userType": "ROLE_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}
