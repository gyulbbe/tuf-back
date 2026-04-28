package io.github.gyulbbe.match.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.match.dto.MatchInfoDto;
import io.github.gyulbbe.match.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RequestMapping("/match")
@RequiredArgsConstructor
@RestController
public class MatchController {

    private final MatchService matchService;

    @PostMapping("/insert")
    public ResponseEntity<ResponseDto<Void>> insertMatchInfo(@Valid @RequestBody MatchInfoDto matchInfoDto) {
        return respond(matchService.insertMatchInfo(matchInfoDto));
    }

    @GetMapping("/get")
    public ResponseEntity<ResponseDto<MatchInfoDto>> findMatchInfoById(@RequestBody MatchInfoDto matchInfoDto) {
        return respond(matchService.findMatchInfoById(matchInfoDto.getId()));
    }

    @PutMapping("/update")
    public ResponseEntity<ResponseDto<Void>> updateMatchInfo(@Valid @RequestBody MatchInfoDto matchInfoDto) {
        return respond(matchService.updateMatchInfo(matchInfoDto.getId(), matchInfoDto));
    }

    @GetMapping("/list")
    public ResponseEntity<ResponseDto<List<MatchInfoDto>>> list() {
        return respond(matchService.list());
    }
}
