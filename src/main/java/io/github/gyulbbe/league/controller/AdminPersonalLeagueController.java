package io.github.gyulbbe.league.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.league.dto.AdminPersonalLeagueCreateRequestDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeagueResponseDto;
import io.github.gyulbbe.league.service.AdminPersonalLeagueService;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/personal-leagues")
public class AdminPersonalLeagueController {

    private final AdminPersonalLeagueService adminPersonalLeagueService;

    @PostMapping
    public ResponseEntity<ResponseDto<AdminPersonalLeagueResponseDto>> createPersonalLeague(
            @RequestBody AdminPersonalLeagueCreateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequired();
            }
            return respond(ResponseDto.success(adminPersonalLeagueService.createPersonalLeague(
                    request,
                    userDetails.getUserPk()
            )));
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to create admin personal league.", e);
            return respond(ResponseDto.fail("Failed to create personal league."));
        }
    }

    @GetMapping("/{leagueId}")
    public ResponseEntity<ResponseDto<AdminPersonalLeagueResponseDto>> getPersonalLeague(@PathVariable Long leagueId) {
        try {
            return respond(ResponseDto.success(adminPersonalLeagueService.getPersonalLeague(leagueId)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            log.warn("Failed to get admin personal league. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("Failed to get personal league."));
        }
    }

    @PutMapping("/{leagueId}")
    public ResponseEntity<ResponseDto<AdminPersonalLeagueResponseDto>> updatePersonalLeague(
            @PathVariable Long leagueId,
            @RequestBody AdminPersonalLeagueCreateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequired();
            }
            return respond(ResponseDto.success(adminPersonalLeagueService.updatePersonalLeague(
                    leagueId,
                    request,
                    userDetails.getUserPk()
            )));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to update admin personal league. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("Failed to update personal league."));
        }
    }

    private <T> ResponseEntity<ResponseDto<T>> validationFailed(IllegalArgumentException e) {
        return respond(ResponseDto.fail(
                HttpServletResponse.SC_BAD_REQUEST,
                e.getMessage(),
                ApiErrorCode.VALIDATION_FAILED
        ));
    }

    private <T> ResponseEntity<ResponseDto<T>> notFound(NoSuchElementException e) {
        return respond(ResponseDto.fail(
                HttpServletResponse.SC_NOT_FOUND,
                e.getMessage(),
                ApiErrorCode.RESOURCE_NOT_FOUND
        ));
    }

    private <T> ResponseEntity<ResponseDto<T>> conflict(IllegalStateException e) {
        return respond(ResponseDto.fail(
                HttpServletResponse.SC_CONFLICT,
                e.getMessage(),
                ApiErrorCode.CONFLICT
        ));
    }

    private <T> ResponseEntity<ResponseDto<T>> authRequired() {
        return respond(ResponseDto.fail(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Authentication is required.",
                ApiErrorCode.AUTH_REQUIRED
        ));
    }
}
