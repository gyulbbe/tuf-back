package io.github.gyulbbe.speech.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.speech.dto.SpeechDto;
import io.github.gyulbbe.speech.service.SpeechService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@Slf4j
@RequestMapping("/speech-learning")
@RequiredArgsConstructor
@RestController
public class SpeechController {

    private final SpeechService speechLearningService;

    /**
     * 채팅 리스트 저장 (임베딩 포함)
     * POST /speech-learning
     */
    @PostMapping("/insert")
    public ResponseEntity<ResponseDto<String>> insertSpeechLearningList(@Valid @RequestBody List<SpeechDto> dtoList) {
        log.info("채팅 리스트 저장 요청 - 개수: {}", dtoList.size());
        ResponseDto<String> response = speechLearningService.insertSpeechLearningList(dtoList);
        return respond(response);
    }
}
