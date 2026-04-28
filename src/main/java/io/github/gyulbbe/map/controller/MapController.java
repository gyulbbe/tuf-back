package io.github.gyulbbe.map.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.map.dto.MapDto;
import io.github.gyulbbe.map.service.MapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RequestMapping("/map")
@RequiredArgsConstructor
@RestController
public class MapController {

    private final MapService mapService;

    @PostMapping("/insert")
    public ResponseEntity<ResponseDto<Void>> insertMap(@Valid @RequestBody MapDto mapDto) {
        return respond(mapService.insertMap(mapDto));
    }

    @GetMapping("/get")
    public ResponseEntity<ResponseDto<MapDto>> findMapById(@RequestBody MapDto mapDto) {
        return respond(mapService.findMapById(mapDto.getId()));
    }

    @PutMapping("/update")
    public ResponseEntity<ResponseDto<Void>> updateMap(@Valid @RequestBody MapDto mapDto) {
        return respond(mapService.updateMap(mapDto.getId(), mapDto));
    }

    @GetMapping("/list")
    public ResponseEntity<ResponseDto<List<MapDto>>> list() {
        return respond(mapService.list());
    }
}
