package io.github.gyulbbe.draft.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.auth.DraftActorResolver;
import io.github.gyulbbe.draft.dto.DraftLivePermissionsResponseDto;
import io.github.gyulbbe.draft.dto.DraftLivePickRequestDto;
import io.github.gyulbbe.draft.dto.DraftLivePreviewPayloadDto;
import io.github.gyulbbe.draft.dto.DraftLiveSnapshotResponseDto;
import io.github.gyulbbe.draft.service.DraftLiveCommandService;
import io.github.gyulbbe.draft.service.DraftLivePreviewRelayService;
import io.github.gyulbbe.draft.service.DraftSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/draft/live")
public class DraftLiveController {

    private final DraftLiveCommandService draftLiveCommandService;
    private final DraftLivePreviewRelayService draftLivePreviewRelayService;
    private final DraftSnapshotService draftSnapshotService;
    private final DraftActorResolver draftActorResolver;

    @GetMapping("/sessions/{sessionId}/snapshot")
    public ResponseEntity<ResponseDto<DraftLiveSnapshotResponseDto>> getSnapshot(@PathVariable Long sessionId) {
        try {
            return respond(ResponseDto.success(
                    draftSnapshotService.getSnapshot(sessionId, draftActorResolver.resolveOptional())
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/permissions")
    public ResponseEntity<ResponseDto<DraftLivePermissionsResponseDto>> getPermissions(@PathVariable Long sessionId) {
        try {
            return respond(ResponseDto.success(
                    draftSnapshotService.getPermissions(sessionId, draftActorResolver.resolveOptional())
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/pick")
    public ResponseEntity<ResponseDto<DraftLiveSnapshotResponseDto>> pick(
            @PathVariable Long sessionId,
            @RequestBody DraftLivePickRequestDto requestDto
    ) {
        try {
            return respond(ResponseDto.success(
                    draftLiveCommandService.pick(
                            sessionId,
                            requestDto.getCandidateUserId(),
                            draftActorResolver.resolveRequired()
                    )
            ));
        } catch (Exception e) {
            return respond(ResponseDto.fail(e.getMessage()));
        }
    }

    @MessageMapping("/drafts/{sessionId}/preview")
    public void relayPreview(
            @DestinationVariable Long sessionId,
            @Payload DraftLivePreviewPayloadDto payload,
            Principal principal,
            @Header("simpSessionId") String connectionSessionId
    ) {
        draftLivePreviewRelayService.relayPreview(
                sessionId,
                payload,
                draftActorResolver.resolve(principal),
                connectionSessionId
        );
    }
}
