package io.github.gyulbbe.transcript.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.transcript.dto.TranscriptRequestDto;
import io.github.gyulbbe.transcript.dto.TranscriptResponseDto;
import io.github.gyulbbe.transcript.service.TranscriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/transcripts")
public class TranscriptController {

    private final TranscriptService transcriptService;

    @PostMapping
    public ResponseEntity<ResponseDto<TranscriptResponseDto>> createTranscript(
            @RequestBody TranscriptRequestDto requestDto
    ) {
        return respond(ResponseDto.success(transcriptService.createTranscript(requestDto)));
    }
}
