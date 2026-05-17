package io.github.gyulbbe.home.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.SecurityConfig;
import io.github.gyulbbe.home.dto.AdminHomeScheduleMapSearchResponse;
import io.github.gyulbbe.home.dto.AdminHomeSchedulePageResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleProleagueTeamSearchResponse;
import io.github.gyulbbe.home.dto.HomeScheduleResponse;
import io.github.gyulbbe.home.service.HomeScheduleService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({HomeScheduleController.class, AdminHomeScheduleController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "tuf-front.url=http://localhost",
        "spring.jwt.secret=12345678901234567890123456789012"
})
class HomeScheduleControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeScheduleService homeScheduleService;

    @MockBean
    private AuthenticationConfiguration authenticationConfiguration;

    @MockBean
    private JWTUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(homeScheduleService.listPublicSchedules(nullable(Integer.class)))
                .thenReturn(ResponseDto.success(List.of(HomeScheduleResponse.builder()
                        .id(1L)
                        .scheduleGroup("PROLEAGUE")
                        .timeLabel("20:00")
                        .title("Season 1 semifinal match 1")
                        .scheduledAt(LocalDateTime.of(2026, 5, 14, 20, 0))
                        .targetUrl("/tournaments/1")
                        .linkType("DIRECT")
                        .navigationUrl("/tournaments/1")
                        .build())));
        when(homeScheduleService.getRedirectTarget(eq(2L))).thenReturn(ResponseDto.success("https://example.com/live"));
        when(homeScheduleService.listAdminSchedules(
                nullable(Integer.class),
                nullable(Integer.class),
                nullable(String.class),
                nullable(java.time.LocalDate.class),
                nullable(java.time.LocalDate.class),
                nullable(String.class)
        ))
                .thenReturn(ResponseDto.success(AdminHomeSchedulePageResponse.builder()
                        .items(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build()));
        when(homeScheduleService.deleteSchedule(eq(1L))).thenReturn(ResponseDto.success(null));
        when(homeScheduleService.searchMaps(nullable(String.class), nullable(Integer.class)))
                .thenReturn(ResponseDto.success(List.of(AdminHomeScheduleMapSearchResponse.builder()
                        .id(10L)
                        .mapName("Fighting Spirit")
                        .image("/maps/fighting-spirit.png")
                        .build())));
        when(homeScheduleService.searchProleagueTeams(nullable(String.class), nullable(Integer.class)))
                .thenReturn(ResponseDto.success(List.of(AdminHomeScheduleProleagueTeamSearchResponse.builder()
                        .teamId(1L)
                        .teamName("Alpha Team")
                        .leagueId(10L)
                        .leagueName("2026 Proleague")
                        .seasonName("Season 1")
                        .build())));
    }

    @Test
    void listPublicSchedules_allowsAnonymousUsers() throws Exception {
        mockMvc.perform(get("/home/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].timeLabel").value("20:00"));
    }

    @Test
    void listAdminSchedules_returnsUnauthorizedWhenAuthenticationMissing() throws Exception {
        mockMvc.perform(get("/admin/home/schedules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void listAdminSchedules_returnsForbiddenForRoleUser() throws Exception {
        mockMvc.perform(get("/admin/home/schedules").with(user("user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));
    }

    @Test
    void listAdminSchedules_allowsRoleAdmin() throws Exception {
        mockMvc.perform(get("/admin/home/schedules").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void listAdminSchedules_allowsRoleManager() throws Exception {
        mockMvc.perform(get("/admin/home/schedules").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void listAdminSchedules_allowsRoleMaster() throws Exception {
        mockMvc.perform(get("/admin/home/schedules").with(user("master").roles("MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void deleteSchedule_returnsNoContentForRoleAdmin() throws Exception {
        mockMvc.perform(delete("/admin/home/schedules/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void searchMaps_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/admin/home/schedules/maps/search").param("keyword", "fight"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/home/schedules/maps/search").param("keyword", "fight").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/home/schedules/maps/search").param("keyword", "fight").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mapName").value("Fighting Spirit"));
    }

    @Test
    void searchProleagueTeams_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/admin/home/schedules/proleague-teams/search").param("keyword", "alpha"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/home/schedules/proleague-teams/search").param("keyword", "alpha").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/home/schedules/proleague-teams/search").param("keyword", "alpha").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teamName").value("Alpha Team"));
    }

    @Test
    void redirect_allowsAnonymousUsers() throws Exception {
        mockMvc.perform(get("/home/schedules/2/redirect"))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", "https://example.com/live"));
    }
}
