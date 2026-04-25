package io.github.gyulbbe.draft.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.auth.DraftActorResolver;
import io.github.gyulbbe.draft.dto.DraftExtendTimeRequestDto;
import io.github.gyulbbe.draft.dto.DraftLiveSnapshotResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickerAssignRequestDto;
import io.github.gyulbbe.draft.dto.DraftReasonRequestDto;
import io.github.gyulbbe.draft.dto.DraftResumeRequestDto;
import io.github.gyulbbe.draft.dto.DraftSessionDetailResponseDto;
import io.github.gyulbbe.draft.service.DraftAdminService;
import io.github.gyulbbe.draft.service.DraftLiveCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/draft/admin")
public class DraftAdminController {

    private final DraftAdminService draftAdminService;
    private final DraftLiveCommandService draftLiveCommandService;
    private final DraftActorResolver draftActorResolver;

    @PostMapping("/teams/{teamId}/picker")
    public ResponseEntity<ResponseDto<DraftSessionDetailResponseDto>> assignPicker(
            @PathVariable Long teamId,
            @RequestBody DraftPickerAssignRequestDto requestDto
    ) {
        try {
            return respond(
                    draftAdminService.assignPicker(
                            teamId,
                            requestDto.resolvePickerUserId(),
                            draftActorResolver.resolveRequired()
                    )
            );
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/start")
    public ResponseEntity<ResponseDto<DraftLiveSnapshotResponseDto>> startSession(@PathVariable Long sessionId) {
        try {
            return respond(ResponseDto.success(
                    draftLiveCommandService.startSession(sessionId, draftActorResolver.resolveRequired())
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/pause")
    public ResponseEntity<ResponseDto<DraftLiveSnapshotResponseDto>> pauseSession(@PathVariable Long sessionId) {
        try {
            return respond(ResponseDto.success(
                    draftLiveCommandService.pauseSession(sessionId, draftActorResolver.resolveRequired())
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/resume")
    public ResponseEntity<ResponseDto<DraftLiveSnapshotResponseDto>> resumeSession(
            @PathVariable Long sessionId,
            @RequestBody(required = false) DraftResumeRequestDto requestDto
    ) {
        try {
            return respond(ResponseDto.success(
                    draftLiveCommandService.resumeSession(
                            sessionId,
                            draftActorResolver.resolveRequired(),
                            requestDto != null ? requestDto.getSeconds() : null
                    )
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/extend-time")
    public ResponseEntity<ResponseDto<DraftLiveSnapshotResponseDto>> extendTime(
            @PathVariable Long sessionId,
            @RequestBody DraftExtendTimeRequestDto requestDto
    ) {
        try {
            return respond(ResponseDto.success(
                    draftLiveCommandService.extendTime(
                            sessionId,
                            draftActorResolver.resolveRequired(),
                            requestDto.getSeconds()
                    )
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/force-skip")
    public ResponseEntity<ResponseDto<DraftLiveSnapshotResponseDto>> forceSkip(
            @PathVariable Long sessionId,
            @RequestBody(required = false) DraftReasonRequestDto requestDto
    ) {
        try {
            return respond(ResponseDto.success(
                    draftLiveCommandService.forceSkip(
                            sessionId,
                            draftActorResolver.resolveRequired(),
                            requestDto != null ? requestDto.getReason() : null
                    )
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/finish")
    public ResponseEntity<ResponseDto<DraftLiveSnapshotResponseDto>> finishSession(
            @PathVariable Long sessionId,
            @RequestBody(required = false) DraftReasonRequestDto requestDto
    ) {
        try {
            return respond(ResponseDto.success(
                    draftLiveCommandService.finishSession(
                            sessionId,
                            draftActorResolver.resolveRequired(),
                            requestDto != null ? requestDto.getReason() : null
                    )
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }
}
