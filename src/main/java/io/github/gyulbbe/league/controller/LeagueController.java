package io.github.gyulbbe.league.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.league.dto.LeagueDto;
import io.github.gyulbbe.league.dto.LeagueParticipationDto;
import io.github.gyulbbe.league.dto.ProleagueTeamDto;
import io.github.gyulbbe.league.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RequestMapping("/league")
@RequiredArgsConstructor
@RestController
public class LeagueController {

    private final LeagueService leagueService;

    @PostMapping("/insert")
    public ResponseEntity<ResponseDto<Void>> insertLeague(@Valid @RequestBody LeagueDto leagueDto) {
        return respond(leagueService.insertLeague(leagueDto));
    }

    @PostMapping("/insert/participation")
    public ResponseEntity<ResponseDto<Void>> insertLeagueParticipation(@Valid @RequestBody LeagueParticipationDto leagueParticipationDto) {
        return respond(leagueService.insertLeagueParticipation(leagueParticipationDto));
    }

    @PostMapping("/insert/team")
    public ResponseEntity<ResponseDto<Void>> insertProleagueTeam(@Valid @RequestBody ProleagueTeamDto proleagueTeamDto) {
        return respond(leagueService.insertProleagueTeam(proleagueTeamDto));
    }

    @GetMapping("/get")
    public ResponseEntity<ResponseDto<LeagueDto>> findLeagueById(@RequestBody LeagueDto leagueDto) {
        return respond(leagueService.findLeagueById(leagueDto.getId()));
    }

    @PutMapping("/update")
    public ResponseEntity<ResponseDto<Void>> updateLeague(@Valid @RequestBody LeagueDto leagueDto) {
        return respond(leagueService.updateLeague(leagueDto.getId(), leagueDto));
    }

    @GetMapping("/get/participation")
    public ResponseEntity<ResponseDto<LeagueParticipationDto>> findLeagueParticipationById(@RequestBody LeagueParticipationDto leagueParticipationDto) {
        return respond(leagueService.findLeagueParticipationById(leagueParticipationDto.getId()));
    }

    @PutMapping("/update/participation")
    public ResponseEntity<ResponseDto<Void>> updateLeagueParticipation(@Valid @RequestBody LeagueParticipationDto leagueParticipationDto) {
        return respond(leagueService.updateLeagueParticipation(leagueParticipationDto.getId(), leagueParticipationDto));
    }

    @GetMapping("/get/team")
    public ResponseEntity<ResponseDto<ProleagueTeamDto>> findProleagueTeamById(@RequestBody ProleagueTeamDto proleagueTeamDto) {
        return respond(leagueService.findProleagueTeamById(proleagueTeamDto.getId()));
    }

    @PutMapping("/update/team")
    public ResponseEntity<ResponseDto<Void>> updateProleagueTeam(@Valid @RequestBody ProleagueTeamDto proleagueTeamDto) {
        return respond(leagueService.updateProleagueTeam(proleagueTeamDto.getId(), proleagueTeamDto));
    }

    @GetMapping("/list")
    public ResponseEntity<ResponseDto<List<LeagueDto>>> list() {
        return respond(leagueService.list());
    }

    @GetMapping("/list/participation")
    public ResponseEntity<ResponseDto<List<LeagueParticipationDto>>> listParticipation() {
        return respond(leagueService.listParticipation());
    }

    @GetMapping("/list/team")
    public ResponseEntity<ResponseDto<List<ProleagueTeamDto>>> listTeam() {
        return respond(leagueService.listTeam());
    }
}
