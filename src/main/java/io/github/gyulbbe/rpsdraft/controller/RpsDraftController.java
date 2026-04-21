package io.github.gyulbbe.rpsdraft.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActorResolver;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftPickerAssignRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftPickerResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionCreateRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionDetailResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionSummaryResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftTeamResponseDto;
import io.github.gyulbbe.rpsdraft.service.RpsDraftAdminService;
import io.github.gyulbbe.rpsdraft.service.RpsDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rps-drafts")
public class RpsDraftController {

    private final RpsDraftService rpsDraftService;
    private final RpsDraftAdminService rpsDraftAdminService;
    private final RpsDraftActorResolver rpsDraftActorResolver;

    @PostMapping("/sessions")
    public ResponseEntity<ResponseDto<RpsDraftSessionDetailResponseDto>> createSession(
            @RequestBody RpsDraftSessionCreateRequestDto requestDto
    ) {
        try {
            return ResponseEntity.ok(rpsDraftService.createSession(requestDto, rpsDraftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return ResponseEntity.ok(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<ResponseDto<List<RpsDraftSessionSummaryResponseDto>>> listSessions() {
        return ResponseEntity.ok(rpsDraftService.listSessions());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<RpsDraftSessionDetailResponseDto>> getSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(rpsDraftService.getSession(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/teams")
    public ResponseEntity<ResponseDto<List<RpsDraftTeamResponseDto>>> listTeams(@PathVariable Long sessionId) {
        return ResponseEntity.ok(rpsDraftService.listTeams(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/teams/{teamId}/picker")
    public ResponseEntity<ResponseDto<RpsDraftPickerResponseDto>> assignPicker(
            @PathVariable Long sessionId,
            @PathVariable Long teamId,
            @RequestBody RpsDraftPickerAssignRequestDto requestDto
    ) {
        try {
            return ResponseEntity.ok(
                    rpsDraftAdminService.assignPicker(
                            sessionId,
                            teamId,
                            requestDto.getPickerUserId(),
                            rpsDraftActorResolver.resolveRequired()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.ok(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/candidates")
    public ResponseEntity<ResponseDto<RpsDraftCandidateResponseDto>> registerCandidate(
            @PathVariable Long sessionId,
            @RequestBody RpsDraftCandidateRequestDto requestDto
    ) {
        try {
            return ResponseEntity.ok(
                    rpsDraftService.registerCandidate(sessionId, requestDto, rpsDraftActorResolver.resolveRequired())
            );
        } catch (Exception e) {
            return ResponseEntity.ok(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/candidates")
    public ResponseEntity<ResponseDto<List<RpsDraftCandidateResponseDto>>> listCandidates(@PathVariable Long sessionId) {
        return ResponseEntity.ok(rpsDraftService.listCandidates(sessionId));
    }
}
