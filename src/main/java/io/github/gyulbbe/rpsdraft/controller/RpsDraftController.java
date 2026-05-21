package io.github.gyulbbe.rpsdraft.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActorResolver;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionCreateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionDetailResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionSummaryResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftTeamResponseDto;
import io.github.gyulbbe.rpsdraft.service.RpsDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rps-drafts")
public class RpsDraftController {

    private final RpsDraftService rpsDraftService;
    private final RpsDraftActorResolver rpsDraftActorResolver;

    @PostMapping("/sessions")
    public ResponseEntity<ResponseDto<RpsDraftSessionDetailResponseDto>> createSession(
            @RequestBody RpsDraftSessionCreateRequestDto requestDto
    ) {
        try {
            return respond(rpsDraftService.createSession(requestDto, rpsDraftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<ResponseDto<List<RpsDraftSessionSummaryResponseDto>>> listSessions() {
        return respond(rpsDraftService.listSessions());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<RpsDraftSessionDetailResponseDto>> getSession(@PathVariable Long sessionId) {
        return respond(rpsDraftService.getSession(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<Void>> deleteSession(@PathVariable Long sessionId) {
        try {
            return respond(rpsDraftService.deleteSession(sessionId, rpsDraftActorResolver.resolveOptional()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/teams")
    public ResponseEntity<ResponseDto<List<RpsDraftTeamResponseDto>>> listTeams(@PathVariable Long sessionId) {
        return respond(rpsDraftService.listTeams(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/candidates")
    public ResponseEntity<ResponseDto<List<RpsDraftCandidateResponseDto>>> listCandidates(@PathVariable Long sessionId) {
        return respond(rpsDraftService.listCandidates(sessionId));
    }
}
