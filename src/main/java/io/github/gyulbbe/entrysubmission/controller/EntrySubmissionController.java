package io.github.gyulbbe.entrysubmission.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.entrysubmission.auth.EntrySubmissionActorResolver;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSessionCreateRequestDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSessionSummaryResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSnapshotResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSubmitRequestDto;
import io.github.gyulbbe.entrysubmission.service.EntrySubmissionCommandService;
import io.github.gyulbbe.entrysubmission.service.EntrySubmissionService;
import io.github.gyulbbe.entrysubmission.service.EntrySubmissionSnapshotService;
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
@RequestMapping("/entry-submissions")
public class EntrySubmissionController {

    private final EntrySubmissionService entrySubmissionService;
    private final EntrySubmissionSnapshotService entrySubmissionSnapshotService;
    private final EntrySubmissionCommandService entrySubmissionCommandService;
    private final EntrySubmissionActorResolver entrySubmissionActorResolver;

    @PostMapping("/sessions")
    public ResponseEntity<ResponseDto<EntrySubmissionSnapshotResponseDto>> createSession(
            @RequestBody EntrySubmissionSessionCreateRequestDto requestDto
    ) {
        try {
            return respond(entrySubmissionService.createSession(requestDto, entrySubmissionActorResolver.resolveRequired()));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<ResponseDto<List<EntrySubmissionSessionSummaryResponseDto>>> listSessions() {
        return respond(entrySubmissionService.listSessions());
    }

    @GetMapping("/sessions/{sessionId}/snapshot")
    public ResponseEntity<ResponseDto<EntrySubmissionSnapshotResponseDto>> getSnapshot(@PathVariable Long sessionId) {
        try {
            return respond(
                    ResponseDto.success(
                            entrySubmissionSnapshotService.getSnapshot(
                                    sessionId,
                                    entrySubmissionActorResolver.resolveOptional()
                            )
                    )
            );
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/submit")
    public ResponseEntity<ResponseDto<EntrySubmissionSnapshotResponseDto>> submitEntries(
            @PathVariable Long sessionId,
            @RequestBody EntrySubmissionSubmitRequestDto requestDto
    ) {
        try {
            return respond(
                    ResponseDto.success(
                            entrySubmissionCommandService.submitEntries(
                                    sessionId,
                                    requestDto,
                                    entrySubmissionActorResolver.resolveRequired()
                            )
                    )
            );
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ResponseDto<Void>> deleteSession(@PathVariable Long sessionId) {
        return respond(entrySubmissionService.deleteSession(sessionId, entrySubmissionActorResolver.resolveOptional()));
    }
}
