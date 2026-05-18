package io.github.gyulbbe.league.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.league.dto.AdminLeagueDeleteResponseDto;
import io.github.gyulbbe.league.dto.AdminLeaguePageResponseDto;
import io.github.gyulbbe.league.dto.AdminLeagueRequestDto;
import io.github.gyulbbe.league.dto.AdminLeagueResponseDto;
import io.github.gyulbbe.league.dto.AdminLeagueSummaryResponseDto;
import io.github.gyulbbe.league.service.AdminLeagueService;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/leagues")
public class AdminLeagueController {

    private final AdminLeagueService adminLeagueService;

    @GetMapping
    public ResponseEntity<ResponseDto<AdminLeaguePageResponseDto>> listLeagues(
            @RequestParam(required = false) String leagueType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String linked
    ) {
        try {
            return respond(ResponseDto.success(adminLeagueService.listLeagues(
                    leagueType,
                    page,
                    size,
                    keyword,
                    status,
                    linked
            )));
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (Exception e) {
            log.warn("Failed to list admin leagues.", e);
            return respond(ResponseDto.fail("Failed to list leagues."));
        }
    }

    @PostMapping
    public ResponseEntity<ResponseDto<AdminLeagueResponseDto>> createLeague(
            @RequestBody AdminLeagueRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequired();
            }
            return respond(ResponseDto.success(adminLeagueService.createLeague(request, userDetails.getUserPk())));
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to create admin league.", e);
            return respond(ResponseDto.fail("Failed to create league."));
        }
    }

    @GetMapping("/{leagueId}")
    public ResponseEntity<ResponseDto<AdminLeagueResponseDto>> getLeague(@PathVariable Long leagueId) {
        try {
            return respond(ResponseDto.success(adminLeagueService.getLeague(leagueId)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            log.warn("Failed to get admin league. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("Failed to get league."));
        }
    }

    @PutMapping("/{leagueId}")
    public ResponseEntity<ResponseDto<AdminLeagueResponseDto>> updateLeague(
            @PathVariable Long leagueId,
            @RequestBody AdminLeagueRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequired();
            }
            return respond(ResponseDto.success(adminLeagueService.updateLeague(leagueId, request, userDetails.getUserPk())));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to update admin league. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("Failed to update league."));
        }
    }

    @PatchMapping("/{leagueId}/finish")
    public ResponseEntity<ResponseDto<AdminLeagueSummaryResponseDto>> finishLeague(@PathVariable Long leagueId) {
        try {
            return respond(ResponseDto.success(adminLeagueService.finishLeague(leagueId)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to finish admin league. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("Failed to finish league."));
        }
    }

    @DeleteMapping("/{leagueId}")
    public ResponseEntity<ResponseDto<AdminLeagueDeleteResponseDto>> deleteLeague(@PathVariable Long leagueId) {
        try {
            return respond(ResponseDto.success(adminLeagueService.deleteLeague(leagueId)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to delete admin league. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("Failed to delete league."));
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
