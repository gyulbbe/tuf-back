package io.github.gyulbbe.map.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.SecurityConfig;
import io.github.gyulbbe.jwt.JWTUtil;
import io.github.gyulbbe.map.dto.AdminMapPageResponse;
import io.github.gyulbbe.map.dto.AdminMapResponse;
import io.github.gyulbbe.map.service.AdminMapService;
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

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMapController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "tuf-front.url=http://localhost",
        "spring.jwt.secret=12345678901234567890123456789012"
})
class AdminMapControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminMapService adminMapService;

    @MockBean
    private AuthenticationConfiguration authenticationConfiguration;

    @MockBean
    private JWTUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(adminMapService.listMaps(nullable(String.class), nullable(Integer.class), nullable(Integer.class)))
                .thenReturn(ResponseDto.success(AdminMapPageResponse.builder()
                        .items(List.of(AdminMapResponse.builder()
                                .id(1L)
                                .mapName("Fighting Spirit")
                                .image("/maps/fighting-spirit.png")
                                .build()))
                        .page(0)
                        .size(20)
                        .totalElements(1)
                        .totalPages(1)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build()));
    }

    @Test
    void listMaps_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/admin/maps"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/maps").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/maps").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].mapName").value("Fighting Spirit"));
    }
}
