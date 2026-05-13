package io.github.gyulbbe.rpsdraft.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActorResolver;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSessionInfoResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSnapshotResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionCreateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionDetailResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftTeamResponseDto;
import io.github.gyulbbe.rpsdraft.service.RpsDraftAdminService;
import io.github.gyulbbe.rpsdraft.service.RpsDraftLiveCommandService;
import io.github.gyulbbe.rpsdraft.service.RpsDraftService;
import io.github.gyulbbe.rpsdraft.service.RpsDraftSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RpsDraftControllerTest {

    @Mock
    private RpsDraftService rpsDraftService;

    @Mock
    private RpsDraftAdminService rpsDraftAdminService;

    @Mock
    private RpsDraftSnapshotService rpsDraftSnapshotService;

    @Mock
    private RpsDraftLiveCommandService rpsDraftLiveCommandService;

    @Mock
    private RpsDraftActorResolver rpsDraftActorResolver;

    @InjectMocks
    private RpsDraftController rpsDraftController;

    @InjectMocks
    private RpsDraftLiveController rpsDraftLiveController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.mockMvc = MockMvcBuilders.standaloneSetup(rpsDraftController, rpsDraftLiveController).build();
    }

    @Test
    void createSession_returns_response_dto_payload() throws Exception {
        RpsDraftActor actor = new RpsDraftActor(10L, "owner", "ROLE_USER");
        RpsDraftSessionDetailResponseDto detail = new RpsDraftSessionDetailResponseDto();
        detail.setId(1L);
        detail.setTitle("session");
        detail.setStatus("READY");

        RpsDraftTeamResponseDto team1 = new RpsDraftTeamResponseDto();
        team1.setId(11L);
        team1.setTeamName("picker-a");
        team1.setDisplayOrder(1);
        team1.setPickerUserId(101L);
        team1.setPickerName("Picker A");

        RpsDraftTeamResponseDto team2 = new RpsDraftTeamResponseDto();
        team2.setId(12L);
        team2.setTeamName("picker-b");
        team2.setDisplayOrder(2);
        team2.setPickerUserId(102L);
        team2.setPickerName("Picker B");

        detail.setTeams(List.of(team1, team2));

        when(rpsDraftActorResolver.resolveRequired()).thenReturn(actor);
        when(rpsDraftService.createSession(any(RpsDraftSessionCreateRequestDto.class), eq(actor)))
                .thenReturn(ResponseDto.success(detail));

        mockMvc.perform(post("/rps-drafts/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.teams.length()").value(2))
                .andExpect(jsonPath("$.data.teams[0].teamName").value("picker-a"))
                .andExpect(jsonPath("$.data.teams[0].pickerUserId").value(101))
                .andExpect(jsonPath("$.data.teams[1].pickerUserId").value(102));
    }

    @Test
    void start_returns_fail_response_when_actor_resolution_fails() throws Exception {
        when(rpsDraftActorResolver.resolveRequired()).thenThrow(new IllegalArgumentException("Authentication is required."));

        mockMvc.perform(post("/rps-drafts/live/sessions/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Authentication is required."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void snapshot_returns_success_payload() throws Exception {
        RpsDraftLiveSessionInfoResponseDto session = new RpsDraftLiveSessionInfoResponseDto();
        session.setId(3L);
        session.setStatus("RPS_PENDING");

        RpsDraftLiveSnapshotResponseDto snapshot = new RpsDraftLiveSnapshotResponseDto();
        snapshot.setSession(session);

        when(rpsDraftActorResolver.resolveOptional()).thenReturn(null);
        when(rpsDraftSnapshotService.getSnapshot(3L, null)).thenReturn(snapshot);

        mockMvc.perform(get("/rps-drafts/live/sessions/3/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.session.id").value(3))
                .andExpect(jsonPath("$.data.session.status").value("RPS_PENDING"));
    }

    @Test
    void deleteSession_returns_http_status_from_response_dto() throws Exception {
        RpsDraftActor actor = new RpsDraftActor(10L, "owner", "ROLE_USER");

        when(rpsDraftActorResolver.resolveOptional()).thenReturn(actor);
        when(rpsDraftService.deleteSession(5L, actor))
                .thenReturn(ResponseDto.fail(403, "Only the session owner or an admin can perform this action."));

        mockMvc.perform(delete("/rps-drafts/sessions/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Only the session owner or an admin can perform this action."));
    }

    private RpsDraftSessionCreateRequestDto createRequest() {
        RpsDraftSessionCreateRequestDto requestDto = new RpsDraftSessionCreateRequestDto();
        requestDto.setTitle("session");
        requestDto.setTeam1PickerUserId(101L);
        requestDto.setTeam2PickerUserId(102L);
        requestDto.setCandidateUserIds(List.of(201L, 202L));
        return requestDto;
    }
}
