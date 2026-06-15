package io.github.gyulbbe.tournament.controller;

import io.github.gyulbbe.config.SecurityConfig;
import io.github.gyulbbe.jwt.JWTUtil;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogSummaryResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentMatchScoreSubmissionEntity;
import io.github.gyulbbe.tournament.entity.RaceSurvivalProgressSubmissionEntity;
import io.github.gyulbbe.tournament.service.RaceSurvivalProgressSubmissionService;
import io.github.gyulbbe.tournament.service.TournamentClanShareSendLogService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private RaceSurvivalProgressSubmissionService raceSurvivalProgressSubmissionService;

    @MockBean
    private TournamentClanShareSendLogService clanShareSendLogService;

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
    void updateMatchParticipants_returnsUnauthorized_whenAuthenticationMissing() throws Exception {
        mockMvc.perform(put("/tournaments/1/matches/100/participants")
                        .contentType("application/json")
                        .content(participantsBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void updateMatchParticipants_returnsOk_forAuthenticatedUser() throws Exception {
        given(tournamentService.assignRaceSurvivalMatchParticipants(
                eq(1L),
                eq(100L),
                eq(1001L),
                eq(1002L),
                eq(101L),
                eq("ROLE_USER")
        )).willReturn(TournamentDetailResponseDto.builder().id(1L).build());

        mockMvc.perform(put("/tournaments/1/matches/100/participants")
                        .with(auth(101L, "ROLE_USER"))
                        .contentType("application/json")
                        .content(participantsBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void raceSurvivalProgressSubmissionSubmitAndList_requireAuthentication() throws Exception {
        mockMvc.perform(post("/tournaments/1/race-survival-progress-submissions")
                        .contentType("application/json")
                        .content(raceProgressBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));

        mockMvc.perform(get("/tournaments/1/race-survival-progress-submissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void raceSurvivalProgressSubmissionSubmitAndList_returnOk_forAuthenticatedUser() throws Exception {
        given(raceSurvivalProgressSubmissionService.submitProgress(eq(1L), any(), eq(101L), eq("ROLE_USER")))
                .willReturn(RaceSurvivalProgressSubmissionResponseDto.builder()
                        .id(900L)
                        .tournamentId(1L)
                        .status(RaceSurvivalProgressSubmissionEntity.STATUS_PENDING)
                        .build());
        given(raceSurvivalProgressSubmissionService.listSubmissions(eq(1L), eq(101L), eq("ROLE_USER")))
                .willReturn(List.of(RaceSurvivalProgressSubmissionResponseDto.builder()
                        .id(900L)
                        .status(RaceSurvivalProgressSubmissionEntity.STATUS_PENDING)
                        .build()));

        mockMvc.perform(post("/tournaments/1/race-survival-progress-submissions")
                        .with(auth(101L, "ROLE_USER"))
                        .contentType("application/json")
                        .content(raceProgressBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(900))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/tournaments/1/race-survival-progress-submissions")
                        .with(auth(101L, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(900));
    }

    @Test
    void raceSurvivalProgressSubmissionApproveAndReject_areAdminOnly() throws Exception {
        mockMvc.perform(post("/tournaments/1/race-survival-progress-submissions/900/approve")
                        .with(auth(101L, "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));

        mockMvc.perform(post("/tournaments/1/race-survival-progress-submissions/900/reject")
                        .with(auth(101L, "ROLE_USER"))
                        .contentType("application/json")
                        .content(rejectBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));
    }

    @Test
    void raceSurvivalProgressSubmissionApproveAndReject_returnOk_forManagerMasterAndAdminRoles() throws Exception {
        given(raceSurvivalProgressSubmissionService.approveSubmission(eq(1L), eq(900L), anyLong(), anyString()))
                .willReturn(TournamentDetailResponseDto.builder().id(1L).build());
        given(raceSurvivalProgressSubmissionService.rejectSubmission(eq(1L), eq(900L), any(), anyLong(), anyString()))
                .willReturn(RaceSurvivalProgressSubmissionResponseDto.builder()
                        .id(900L)
                        .status(RaceSurvivalProgressSubmissionEntity.STATUS_REJECTED)
                        .build());

        for (String role : List.of("ROLE_MANAGER", "ROLE_MASTER", "ROLE_ADMIN")) {
            mockMvc.perform(post("/tournaments/1/race-survival-progress-submissions/900/approve")
                            .with(auth(999L, role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(1));

            mockMvc.perform(post("/tournaments/1/race-survival-progress-submissions/900/reject")
                            .with(auth(999L, role))
                            .contentType("application/json")
                            .content(rejectBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(900))
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
        }
    }

    @Test
    void clanShareSendLogSummaryAndCreate_areAdminOnly() throws Exception {
        mockMvc.perform(get("/tournaments/1/clan-share-send-logs/summary")
                        .with(auth(101L, "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));

        mockMvc.perform(post("/tournaments/clan-share-send-logs")
                        .with(auth(101L, "ROLE_USER"))
                        .contentType("application/json")
                        .content(clanShareLogBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("AUTH_FORBIDDEN"));
    }

    @Test
    void clanShareSendLogSummaryAndCreate_returnOk_forManagerMasterAndAdminRoles() throws Exception {
        given(clanShareSendLogService.getSummary(eq(1L)))
                .willReturn(TournamentClanShareSendLogSummaryResponseDto.builder()
                        .hasHistory(true)
                        .totalCount(1)
                        .build());
        given(clanShareSendLogService.createLog(any(), anyLong()))
                .willReturn(TournamentClanShareSendLogResponseDto.builder()
                        .id(700L)
                        .tournamentId(1L)
                        .matchId(100L)
                        .eloStatus("SUCCESS")
                        .sheetStatus("SUCCESS")
                        .build());

        for (String role : List.of("ROLE_MANAGER", "ROLE_MASTER", "ROLE_ADMIN")) {
            mockMvc.perform(get("/tournaments/1/clan-share-send-logs/summary")
                            .with(auth(999L, role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.hasHistory").value(true))
                    .andExpect(jsonPath("$.data.totalCount").value(1));

            mockMvc.perform(post("/tournaments/clan-share-send-logs")
                            .with(auth(999L, role))
                            .contentType("application/json")
                            .content(clanShareLogBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(700))
                    .andExpect(jsonPath("$.data.eloStatus").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.sheetStatus").value("SUCCESS"));
        }
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

    private String participantsBody() {
        return """
                {
                  "slot1ParticipantId": 1001,
                  "slot2ParticipantId": 1002
                }
                """;
    }

    private String raceProgressBody() {
        return """
                {
                  "matches": [
                    {
                      "matchOrder": 1,
                      "slot1ParticipantId": 1001,
                      "slot2ParticipantId": 2001,
                      "slot1Score": 1,
                      "slot2Score": 0
                    }
                  ]
                }
                """;
    }

    private String clanShareLogBody() {
        return """
                {
                  "tournamentId": 1,
                  "matchId": 100,
                  "sendGroupId": "00000000-0000-0000-0000-000000000000",
                  "player1": "A",
                  "player2": "B",
                  "winner": "A",
                  "loser": "B",
                  "mapName": "투혼",
                  "matchType": "개인리그",
                  "matchName": "테스트 대회",
                  "playedDate": "2026-05-31",
                  "eloStatus": "SUCCESS",
                  "eloMessage": "SUCCESS",
                  "sheetStatus": "SUCCESS",
                  "sheetMessage": "SUCCESS"
                }
                """;
    }
}
