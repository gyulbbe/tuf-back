package io.github.gyulbbe.home.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.home.dto.HomeScheduleResponse;
import io.github.gyulbbe.home.service.HomeScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
public class HomeScheduleController {

    private final HomeScheduleService homeScheduleService;

    @GetMapping("/home/schedules")
    public ResponseEntity<ResponseDto<List<HomeScheduleResponse>>> listPublicSchedules(
            @RequestParam(required = false) Integer limit
    ) {
        return respond(homeScheduleService.listPublicSchedules(limit));
    }

    @GetMapping("/home/schedules/{scheduleId}/redirect")
    public ResponseEntity<?> redirect(@PathVariable Long scheduleId) {
        ResponseDto<String> response = homeScheduleService.getRedirectTarget(scheduleId);
        if (response.getStatus() == 200) {
            return ResponseEntity.status(302)
                    .location(URI.create(response.getData()))
                    .build();
        }
        return respond(response);
    }
}
