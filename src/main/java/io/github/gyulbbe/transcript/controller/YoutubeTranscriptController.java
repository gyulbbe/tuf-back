package io.github.gyulbbe.transcript.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.transcript.dto.YoutubeTranscriptRequestDto;
import io.github.gyulbbe.transcript.dto.YoutubeTranscriptResponseDto;
import io.github.gyulbbe.transcript.service.YoutubeTranscriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/transcripts")
public class YoutubeTranscriptController {

    private final YoutubeTranscriptService youtubeTranscriptService;

    @PostMapping("/youtube")
    public ResponseEntity<ResponseDto<YoutubeTranscriptResponseDto>> createYoutubeTranscript(
            @RequestBody YoutubeTranscriptRequestDto requestDto
    ) {
        return respond(ResponseDto.success(youtubeTranscriptService.createYoutubeTranscript(requestDto)));
    }
}
