package io.github.gyulbbe.rpsdraft.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActorResolver;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSnapshotResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftPickRequestDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftRpsSubmitRequestDto;
import io.github.gyulbbe.rpsdraft.service.RpsDraftLiveCommandService;
import io.github.gyulbbe.rpsdraft.service.RpsDraftSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rps-drafts/live")
public class RpsDraftLiveController {

    private final RpsDraftSnapshotService rpsDraftSnapshotService;
    private final RpsDraftLiveCommandService rpsDraftLiveCommandService;
    private final RpsDraftActorResolver rpsDraftActorResolver;

    @GetMapping("/sessions/{sessionId}/snapshot")
    public ResponseEntity<ResponseDto<RpsDraftLiveSnapshotResponseDto>> getSnapshot(@PathVariable Long sessionId) {
        try {
            return respond(
                    ResponseDto.success(rpsDraftSnapshotService.getSnapshot(sessionId, rpsDraftActorResolver.resolveOptional()))
            );
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/start")
    public ResponseEntity<ResponseDto<RpsDraftLiveSnapshotResponseDto>> start(@PathVariable Long sessionId) {
        try {
            return respond(
                    ResponseDto.success(rpsDraftLiveCommandService.startSession(sessionId, rpsDraftActorResolver.resolveRequired()))
            );
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/rps/submit")
    public ResponseEntity<ResponseDto<RpsDraftLiveSnapshotResponseDto>> submitRps(
            @PathVariable Long sessionId,
            @RequestBody RpsDraftRpsSubmitRequestDto requestDto
    ) {
        try {
            return respond(
                    ResponseDto.success(
                            rpsDraftLiveCommandService.submitRps(
                                    sessionId,
                                    requestDto.getChoice(),
                                    rpsDraftActorResolver.resolveRequired()
                            )
                    )
            );
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/pick")
    public ResponseEntity<ResponseDto<RpsDraftLiveSnapshotResponseDto>> pick(
            @PathVariable Long sessionId,
            @RequestBody RpsDraftPickRequestDto requestDto
    ) {
        try {
            return respond(
                    ResponseDto.success(
                            rpsDraftLiveCommandService.pick(
                                    sessionId,
                                    requestDto.getCandidateUserId(),
                                    rpsDraftActorResolver.resolveRequired()
                            )
                    )
            );
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/finish")
    public ResponseEntity<ResponseDto<RpsDraftLiveSnapshotResponseDto>> finish(@PathVariable Long sessionId) {
        try {
            return respond(
                    ResponseDto.success(
                            rpsDraftLiveCommandService.finishSession(sessionId, rpsDraftActorResolver.resolveRequired())
                    )
            );
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }
}
