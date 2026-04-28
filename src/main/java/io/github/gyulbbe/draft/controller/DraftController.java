package io.github.gyulbbe.draft.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.auth.DraftActorResolver;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderBulkReplaceRequestDto;
import io.github.gyulbbe.draft.dto.DraftOrderRequestDto;
import io.github.gyulbbe.draft.dto.DraftOrderResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickRequestDto;
import io.github.gyulbbe.draft.dto.DraftPickResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionDetailResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionRequestDto;
import io.github.gyulbbe.draft.dto.DraftSessionSummaryResponseDto;
import io.github.gyulbbe.draft.dto.DraftTeamRequestDto;
import io.github.gyulbbe.draft.dto.DraftTeamResponseDto;
import io.github.gyulbbe.draft.service.DraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/draft")
public class DraftController {

    private final DraftService draftService;
    private final DraftActorResolver draftActorResolver;

    @PostMapping("/sessions")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> createSession(@RequestBody DraftSessionRequestDto requestDto) {
        try {
            return respond(draftService.createSession(requestDto, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<ResponseDto<List<DraftSessionSummaryResponseDto>>> listSessions() {
        return respond(draftService.listSessions());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> getSession(@PathVariable Long sessionId) {
        return respond(draftService.getSession(sessionId));
    }

    @PutMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> updateSession(
            @PathVariable Long sessionId,
            @RequestBody DraftSessionRequestDto requestDto
    ) {
        try {
            return respond(draftService.updateSession(sessionId, requestDto, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<Void>> deleteSession(@PathVariable Long sessionId) {
        try {
            return respond(draftService.deleteSession(sessionId, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/teams")
    public ResponseEntity<ResponseDto<DraftTeamResponseDto>> createTeam(@RequestBody DraftTeamRequestDto requestDto) {
        try {
            return respond(draftService.createTeam(requestDto, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/teams")
    public ResponseEntity<ResponseDto<List<DraftTeamResponseDto>>> listTeams(@PathVariable Long sessionId) {
        return respond(draftService.listTeams(sessionId));
    }

    @GetMapping("/teams/{teamId}")
    public ResponseEntity<ResponseDto<DraftTeamResponseDto>> getTeam(@PathVariable Long teamId) {
        return respond(draftService.getTeam(teamId));
    }

    @PutMapping("/teams/{teamId}")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> updateTeam(
            @PathVariable Long teamId,
            @RequestBody DraftTeamRequestDto requestDto
    ) {
        try {
            return respond(draftService.updateTeam(teamId, requestDto, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<ResponseDto<Void>> deleteTeam(@PathVariable Long teamId) {
        try {
            return respond(draftService.deleteTeam(teamId, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/candidates")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> createCandidate(@RequestBody DraftCandidateRequestDto requestDto) {
        try {
            return respond(draftService.createCandidate(requestDto, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/candidates")
    public ResponseEntity<ResponseDto<List<DraftCandidateResponseDto>>> listCandidates(@PathVariable Long sessionId) {
        return respond(draftService.listCandidates(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/candidates/{candidateUserId}")
    public ResponseEntity<ResponseDto<DraftCandidateResponseDto>> getCandidate(
            @PathVariable Long sessionId,
            @PathVariable Long candidateUserId
    ) {
        return respond(draftService.getCandidate(sessionId, candidateUserId));
    }

    @PutMapping("/sessions/{sessionId}/candidates/{candidateUserId}")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> updateCandidate(
            @PathVariable Long sessionId,
            @PathVariable Long candidateUserId,
            @RequestBody DraftCandidateRequestDto requestDto
    ) {
        try {
            return respond(draftService.updateCandidate(
                    sessionId,
                    candidateUserId,
                    requestDto,
                    draftActorResolver.resolveRequired()
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @DeleteMapping("/sessions/{sessionId}/candidates/{candidateUserId}")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> deleteCandidate(@PathVariable Long sessionId, @PathVariable Long candidateUserId) {
        try {
            return respond(draftService.deleteCandidate(sessionId, candidateUserId, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/orders")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> createOrder(@RequestBody DraftOrderRequestDto requestDto) {
        try {
            return respond(draftService.createOrder(requestDto, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/orders")
    public ResponseEntity<ResponseDto<List<DraftOrderResponseDto>>> listOrders(@PathVariable Long sessionId) {
        return respond(draftService.listOrders(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/orders/{pickNo}")
    public ResponseEntity<ResponseDto<DraftOrderResponseDto>> getOrder(@PathVariable Long sessionId, @PathVariable Long pickNo) {
        return respond(draftService.getOrder(sessionId, pickNo));
    }

    @PutMapping("/sessions/{sessionId}/orders")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> replaceOrders(
            @PathVariable Long sessionId,
            @RequestBody DraftOrderBulkReplaceRequestDto requestDto
    ) {
        try {
            return respond(draftService.replaceOrders(sessionId, requestDto, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PutMapping("/sessions/{sessionId}/orders/{pickNo}")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> updateOrder(
            @PathVariable Long sessionId,
            @PathVariable Long pickNo,
            @RequestBody DraftOrderRequestDto requestDto
    ) {
        try {
            return respond(draftService.updateOrder(
                    sessionId,
                    pickNo,
                    requestDto,
                    draftActorResolver.resolveRequired()
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @DeleteMapping("/sessions/{sessionId}/orders/{pickNo}")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> deleteOrder(@PathVariable Long sessionId, @PathVariable Long pickNo) {
        try {
            return respond(draftService.deleteOrder(sessionId, pickNo, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/picks")
    public ResponseEntity<ResponseDto<DraftPickResponseDto>> createPick(@RequestBody DraftPickRequestDto requestDto) {
        try {
            return respond(draftService.createPick(requestDto, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/picks")
    public ResponseEntity<ResponseDto<List<DraftPickResponseDto>>> listPicks(@PathVariable Long sessionId) {
        return respond(draftService.listPicks(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/picks/{pickNo}")
    public ResponseEntity<ResponseDto<DraftPickResponseDto>> getPick(@PathVariable Long sessionId, @PathVariable Long pickNo) {
        return respond(draftService.getPick(sessionId, pickNo));
    }

    @PutMapping("/sessions/{sessionId}/picks/{pickNo}")
    public ResponseEntity<ResponseDto<DraftPickResponseDto>> updatePick(
            @PathVariable Long sessionId,
            @PathVariable Long pickNo,
            @RequestBody DraftPickRequestDto requestDto
    ) {
        try {
            return respond(draftService.updatePick(
                    sessionId,
                    pickNo,
                    requestDto,
                    draftActorResolver.resolveRequired()
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @DeleteMapping("/sessions/{sessionId}/picks/{pickNo}")
    public ResponseEntity<ResponseDto<Void>> deletePick(@PathVariable Long sessionId, @PathVariable Long pickNo) {
        try {
            return respond(draftService.deletePick(sessionId, pickNo, draftActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }
}
