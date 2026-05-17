package io.github.gyulbbe.home.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.home.dto.AdminHomeScheduleCreateRequest;
import io.github.gyulbbe.home.dto.AdminHomeScheduleDeleteRequest;
import io.github.gyulbbe.home.dto.AdminHomeScheduleDeleteResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleMapSearchResponse;
import io.github.gyulbbe.home.dto.AdminHomeSchedulePageResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleProleagueTeamSearchResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleUpdateRequest;
import io.github.gyulbbe.home.service.HomeScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/home/schedules")
public class AdminHomeScheduleController {

    private final HomeScheduleService homeScheduleService;

    @GetMapping("/maps/search")
    public ResponseEntity<ResponseDto<List<AdminHomeScheduleMapSearchResponse>>> searchMaps(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit
    ) {
        return respond(homeScheduleService.searchMaps(keyword, limit));
    }

    @GetMapping("/proleague-teams/search")
    public ResponseEntity<ResponseDto<List<AdminHomeScheduleProleagueTeamSearchResponse>>> searchProleagueTeams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit
    ) {
        return respond(homeScheduleService.searchProleagueTeams(keyword, limit));
    }

    @GetMapping
    public ResponseEntity<ResponseDto<AdminHomeSchedulePageResponse>> listSchedules(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String scheduleGroup
    ) {
        return respond(homeScheduleService.listAdminSchedules(page, size, keyword, fromDate, toDate, scheduleGroup));
    }

    @PostMapping
    public ResponseEntity<ResponseDto<AdminHomeScheduleResponse>> createSchedule(
            @RequestBody AdminHomeScheduleCreateRequest request
    ) {
        return respond(homeScheduleService.createSchedule(request));
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<ResponseDto<AdminHomeScheduleResponse>> updateSchedule(
            @PathVariable Long scheduleId,
            @RequestBody AdminHomeScheduleUpdateRequest request
    ) {
        return respond(homeScheduleService.updateSchedule(scheduleId, request));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<ResponseDto<Void>> deleteSchedule(@PathVariable Long scheduleId) {
        ResponseDto<Void> response = homeScheduleService.deleteSchedule(scheduleId);
        if (response.getStatus() == 200) {
            return ResponseEntity.noContent().build();
        }
        return respond(response);
    }

    @PostMapping("/delete")
    public ResponseEntity<ResponseDto<AdminHomeScheduleDeleteResponse>> deleteSchedules(
            @RequestBody AdminHomeScheduleDeleteRequest request
    ) {
        return respond(homeScheduleService.deleteSchedules(request));
    }
}
