package io.github.gyulbbe.map.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.map.dto.AdminMapPageResponse;
import io.github.gyulbbe.map.dto.AdminMapRequest;
import io.github.gyulbbe.map.dto.AdminMapResponse;
import io.github.gyulbbe.map.service.AdminMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/maps")
public class AdminMapController {

    private final AdminMapService adminMapService;

    @GetMapping
    public ResponseEntity<ResponseDto<AdminMapPageResponse>> listMaps(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return respond(adminMapService.listMaps(keyword, page, size));
    }

    @PostMapping
    public ResponseEntity<ResponseDto<AdminMapResponse>> createMap(@RequestBody AdminMapRequest request) {
        return respond(adminMapService.createMap(request));
    }

    @PutMapping("/{mapId}")
    public ResponseEntity<ResponseDto<AdminMapResponse>> updateMap(
            @PathVariable Long mapId,
            @RequestBody AdminMapRequest request
    ) {
        return respond(adminMapService.updateMap(mapId, request));
    }

    @DeleteMapping("/{mapId}")
    public ResponseEntity<ResponseDto<Void>> deleteMap(@PathVariable Long mapId) {
        return respond(adminMapService.deleteMap(mapId));
    }
}
