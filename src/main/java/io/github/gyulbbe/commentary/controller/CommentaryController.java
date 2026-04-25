package io.github.gyulbbe.commentary.controller;

import io.github.gyulbbe.commentary.dto.CommentaryDto;
import io.github.gyulbbe.commentary.service.CommentaryService;
import io.github.gyulbbe.common.dto.ResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RequestMapping("/commentary")
@RequiredArgsConstructor
@RestController
public class CommentaryController {

    private final CommentaryService commentaryService;

    @PostMapping("/insert")
    public ResponseEntity<ResponseDto<Void>> insertCommentary(@Valid @RequestBody CommentaryDto commentaryDto) {
        return respond(commentaryService.insertCommentary(commentaryDto));
    }

    @PostMapping("/embed-all")
    public ResponseEntity<ResponseDto<String>> embedAllCommentaries() {
        return respond(commentaryService.embedAllCommentaries());
    }
}
