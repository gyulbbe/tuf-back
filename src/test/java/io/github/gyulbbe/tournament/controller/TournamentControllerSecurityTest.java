package io.github.gyulbbe.tournament.controller;

import io.github.gyulbbe.config.SecurityConfig;
import io.github.gyulbbe.jwt.JWTUtil;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentMatchScoreSubmissionEntity;
import io.github.gyulbbe.tournament.service.TournamentCreationService;
import io.github.gyulbbe.tournament.service.TournamentMatchScoreSubmissionService;
import io.github.gyulbbe.tournament.service.TournamentService;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import io.github.gyulbbe.user.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TournamentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "tuf-front.url=http://localhost",
        "spring.jwt.secret=12345678901234567890123456789012"
})
class TournamentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TournamentService tournamentService;

    @MockBean
    private TournamentCreationService tournamentCreationService;

    @MockBean
    private TournamentMatchScoreSubmissionService scoreSubmissionService;

    @MockBean
    private AuthenticationConfiguration authenticationConfiguration;

    @MockBean
    private JWTUtil jwtUtil;

    @Test
    void submitScore_returnsUnauthorized_whenAuthenticationMissing() throws Exception {
        mockMvc.perform(post("/tournaments/1/matches/100/score-submissions")
                        .contentType("application/json")
                        .content(scoreBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void listScoreSubmissions_returnsUnauthorized_whenAuthenticationMissing() throws Exception {
        mockMvc.perform(get("/tournaments/1/matches/100/score-submissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void submitAndListScoreSubmissions_returnOk_forAuthenticatedUser() throws Exception {
        given(scoreSubmissionService.submitScore(eq(1L), eq(100L), any(), eq(101L), eq("ROLE_USER")))
                .willReturn(TournamentScoreSubmissionResponseDto.builder()
                        .submissionId(900L)
                        .matchId(100L)
                        .status(TournamentMatchScoreSubmissionEntity.STATUS_PENDING)
                        .winnerSlotNo(1)
                        .build());
        given(scoreSubmissionService.listSubmissions(eq(1L), eq(100L), eq(101L), eq("ROLE_USER")))
                .willReturn(List.of(TournamentScoreSubmissionResponseDto.builder().id(900L).build()));

        mockMvc.perform(post("/tournaments/1/matches/100/score-submissions")
                        .with(auth(101L, "ROLE_USER"))
                        .contentType("application/json")
                        .content(scoreBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.submissionId").value(900))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/tournaments/1/matches/100/score-submissions")
                        .with(auth(101L, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(900));
    }

    @Test
    void approveAndRejectScoreSubmissions_returnForbidden_forRoleUser() throws Exception {
        mockMvc.perform(post("/tournaments/1/matches/100/score-submissions/900/approve")
                        .with(auth(101L, "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));

        mockMvc.perform(post("/tournaments/1/matches/100/score-submissions/900/reject")
                        .with(auth(101L, "ROLE_USER"))
                        .contentType("application/json")
                        .content(rejectBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));
    }

    @Test
    void approveScoreSubmission_returnsOk_forManagerMasterAndAdminRoles() throws Exception {
        given(scoreSubmissionService.approveSubmission(eq(1L), eq(100L), eq(900L), anyLong(), anyString()))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        for (String role : List.of("ROLE_MANAGER", "ROLE_MASTER", "ROLE_ADMIN")) {
            mockMvc.perform(post("/tournaments/1/matches/100/score-submissions/900/approve")
                            .with(auth(999L, role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("success"))
                    .andExpect(jsonPath("$.data.id").value(1));
        }
    }

    @Test
    void rejectScoreSubmission_returnsOk_forManagerMasterAndAdminRoles() throws Exception {
        given(scoreSubmissionService.rejectSubmission(eq(1L), eq(100L), eq(900L), any(), anyLong(), anyString()))
                .willReturn(TournamentScoreSubmissionResponseDto.builder()
                        .id(900L)
                        .status(TournamentMatchScoreSubmissionEntity.STATUS_REJECTED)
                        .build());

        for (String role : List.of("ROLE_MANAGER", "ROLE_MASTER", "ROLE_ADMIN")) {
            mockMvc.perform(post("/tournaments/1/matches/100/score-submissions/900/reject")
                            .with(auth(999L, role))
                            .contentType("application/json")
                            .content(rejectBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(900))
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
        }
    }

    private RequestPostProcessor auth(Long userId, String role) {
        CustomUserDetails userDetails = new CustomUserDetails(UserEntity.builder()
                .id(userId)
                .userId("user" + userId)
                .password("password")
                .userType(role)
                .build());
        return authentication(new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        ));
    }

    private String scoreBody() {
        return """
                {
                  "scores": [
                    { "slotNo": 1, "score": 2 },
                    { "slotNo": 2, "score": 1 }
                  ]
                }
                """;
    }

    private String rejectBody() {
        return """
                {
                  "adminNote": "score mismatch"
                }
                """;
    }
}
