package io.github.gyulbbe.draft.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.dto.*;
import io.github.gyulbbe.draft.service.DraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/draft")
public class DraftController {

    private final DraftService draftService;

    @PostMapping("/sessions")
    public ResponseEntity<ResponseDto<DraftSessionSummaryResponseDto>> createSession(@RequestBody DraftSessionRequestDto requestDto) {
        return ResponseEntity.ok(draftService.createSession(requestDto));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ResponseDto<List<DraftSessionSummaryResponseDto>>> listSessions() {
        return ResponseEntity.ok(draftService.listSessions());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> getSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(draftService.getSession(sessionId));
    }

    @PutMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<DraftSessionSummaryResponseDto>> updateSession(
            @PathVariable Long sessionId,
            @RequestBody DraftSessionRequestDto requestDto
    ) {
        return ResponseEntity.ok(draftService.updateSession(sessionId, requestDto));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<Void>> deleteSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(draftService.deleteSession(sessionId));
    }

    @PostMapping("/teams")
    public ResponseEntity<ResponseDto<DraftTeamResponseDto>> createTeam(@RequestBody DraftTeamRequestDto requestDto) {
        return ResponseEntity.ok(draftService.createTeam(requestDto));
    }

    @GetMapping("/sessions/{sessionId}/teams")
    public ResponseEntity<ResponseDto<List<DraftTeamResponseDto>>> listTeams(@PathVariable Long sessionId) {
        return ResponseEntity.ok(draftService.listTeams(sessionId));
    }

    @GetMapping("/teams/{teamId}")
    public ResponseEntity<ResponseDto<DraftTeamResponseDto>> getTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(draftService.getTeam(teamId));
    }

    @PutMapping("/teams/{teamId}")
    public ResponseEntity<ResponseDto<DraftTeamResponseDto>> updateTeam(
            @PathVariable Long teamId,
            @RequestBody DraftTeamRequestDto requestDto
    ) {
        return ResponseEntity.ok(draftService.updateTeam(teamId, requestDto));
    }

    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<ResponseDto<Void>> deleteTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(draftService.deleteTeam(teamId));
    }

    @PostMapping("/candidates")
    public ResponseEntity<ResponseDto<DraftCandidateResponseDto>> createCandidate(@RequestBody DraftCandidateRequestDto requestDto) {
        return ResponseEntity.ok(draftService.createCandidate(requestDto));
    }

    @GetMapping("/sessions/{sessionId}/candidates")
    public ResponseEntity<ResponseDto<List<DraftCandidateResponseDto>>> listCandidates(@PathVariable Long sessionId) {
        return ResponseEntity.ok(draftService.listCandidates(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/candidates/{candidateUserId}")
    public ResponseEntity<ResponseDto<DraftCandidateResponseDto>> getCandidate(
            @PathVariable Long sessionId,
            @PathVariable Long candidateUserId
    ) {
        return ResponseEntity.ok(draftService.getCandidate(sessionId, candidateUserId));
    }

    @PutMapping("/sessions/{sessionId}/candidates/{candidateUserId}")
    public ResponseEntity<ResponseDto<DraftCandidateResponseDto>> updateCandidate(
            @PathVariable Long sessionId,
            @PathVariable Long candidateUserId,
            @RequestBody DraftCandidateRequestDto requestDto
    ) {
        return ResponseEntity.ok(draftService.updateCandidate(sessionId, candidateUserId, requestDto));
    }

    @DeleteMapping("/sessions/{sessionId}/candidates/{candidateUserId}")
    public ResponseEntity<ResponseDto<Void>> deleteCandidate(@PathVariable Long sessionId, @PathVariable Long candidateUserId) {
        return ResponseEntity.ok(draftService.deleteCandidate(sessionId, candidateUserId));
    }

    @PostMapping("/orders")
    public ResponseEntity<ResponseDto<DraftOrderResponseDto>> createOrder(@RequestBody DraftOrderRequestDto requestDto) {
        return ResponseEntity.ok(draftService.createOrder(requestDto));
    }

    @GetMapping("/sessions/{sessionId}/orders")
    public ResponseEntity<ResponseDto<List<DraftOrderResponseDto>>> listOrders(@PathVariable Long sessionId) {
        return ResponseEntity.ok(draftService.listOrders(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/orders/{pickNo}")
    public ResponseEntity<ResponseDto<DraftOrderResponseDto>> getOrder(@PathVariable Long sessionId, @PathVariable Long pickNo) {
        return ResponseEntity.ok(draftService.getOrder(sessionId, pickNo));
    }

    @PutMapping("/sessions/{sessionId}/orders/{pickNo}")
    public ResponseEntity<ResponseDto<DraftOrderResponseDto>> updateOrder(
            @PathVariable Long sessionId,
            @PathVariable Long pickNo,
            @RequestBody DraftOrderRequestDto requestDto
    ) {
        return ResponseEntity.ok(draftService.updateOrder(sessionId, pickNo, requestDto));
    }

    @DeleteMapping("/sessions/{sessionId}/orders/{pickNo}")
    public ResponseEntity<ResponseDto<Void>> deleteOrder(@PathVariable Long sessionId, @PathVariable Long pickNo) {
        return ResponseEntity.ok(draftService.deleteOrder(sessionId, pickNo));
    }

    @PostMapping("/picks")
    public ResponseEntity<ResponseDto<DraftPickResponseDto>> createPick(@RequestBody DraftPickRequestDto requestDto) {
        return ResponseEntity.ok(draftService.createPick(requestDto));
    }

    @GetMapping("/sessions/{sessionId}/picks")
    public ResponseEntity<ResponseDto<List<DraftPickResponseDto>>> listPicks(@PathVariable Long sessionId) {
        return ResponseEntity.ok(draftService.listPicks(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/picks/{pickNo}")
    public ResponseEntity<ResponseDto<DraftPickResponseDto>> getPick(@PathVariable Long sessionId, @PathVariable Long pickNo) {
        return ResponseEntity.ok(draftService.getPick(sessionId, pickNo));
    }

    @PutMapping("/sessions/{sessionId}/picks/{pickNo}")
    public ResponseEntity<ResponseDto<DraftPickResponseDto>> updatePick(
            @PathVariable Long sessionId,
            @PathVariable Long pickNo,
            @RequestBody DraftPickRequestDto requestDto
    ) {
        return ResponseEntity.ok(draftService.updatePick(sessionId, pickNo, requestDto));
    }

    @DeleteMapping("/sessions/{sessionId}/picks/{pickNo}")
    public ResponseEntity<ResponseDto<Void>> deletePick(@PathVariable Long sessionId, @PathVariable Long pickNo) {
        return ResponseEntity.ok(draftService.deletePick(sessionId, pickNo));
    }
}
