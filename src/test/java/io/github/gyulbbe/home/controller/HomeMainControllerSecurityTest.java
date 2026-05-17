package io.github.gyulbbe.home.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.SecurityConfig;
import io.github.gyulbbe.home.dto.HomeMainResponse;
import io.github.gyulbbe.home.service.HomeMainService;
import io.github.gyulbbe.jwt.JWTUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeMainController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "tuf-front.url=http://localhost",
        "spring.jwt.secret=12345678901234567890123456789012"
})
class HomeMainControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeMainService homeMainService;

    @MockBean
    private AuthenticationConfiguration authenticationConfiguration;

    @MockBean
    private JWTUtil jwtUtil;

    @Test
    void getHomeMain_allowsAnonymousUsers() throws Exception {
        when(homeMainService.getHomeMain())
                .thenReturn(ResponseDto.success(HomeMainResponse.builder().build()));

        mockMvc.perform(get("/home/main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.proleagueSchedules").isArray())
                .andExpect(jsonPath("$.data.personalLeagueSchedules").isArray())
                .andExpect(jsonPath("$.data.ongoing").isArray())
                .andExpect(jsonPath("$.data.botAlerts").isArray())
                .andExpect(jsonPath("$.data.galleryPosts").isArray())
                .andExpect(jsonPath("$.data.schedules").isArray());
    }
}
