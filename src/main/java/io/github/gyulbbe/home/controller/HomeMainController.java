package io.github.gyulbbe.home.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.home.dto.HomeMainResponse;
import io.github.gyulbbe.home.service.HomeMainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
public class HomeMainController {

    private final HomeMainService homeMainService;

    @GetMapping("/home/main")
    public ResponseEntity<ResponseDto<HomeMainResponse>> getHomeMain() {
        return respond(homeMainService.getHomeMain());
    }
}
