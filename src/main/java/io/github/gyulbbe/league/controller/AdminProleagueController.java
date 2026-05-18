package io.github.gyulbbe.league.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.draft.dto.DraftSessionSummaryResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueCreateRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueDeleteRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueDeleteResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueFinishRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueHistoryPageResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueHistoryResponseDto;
import io.github.gyulbbe.league.dto.AdminProleaguePageResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueResponseDto;
import io.github.gyulbbe.league.service.AdminProleagueService;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.NoSuchElementException;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/proleagues")
public class AdminProleagueController {

    private final AdminProleagueService adminProleagueService;

    @PostMapping
    public ResponseEntity<ResponseDto<AdminProleagueResponseDto>> createProleague(
            @RequestBody AdminProleagueCreateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequired();
            }
            Long adminUserId = userDetails.getUserPk();
            return respond(ResponseDto.success(adminProleagueService.createProleague(request, adminUserId)));
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to create admin proleague.", e);
            return respond(ResponseDto.fail("프로리그 등록에 실패했습니다."));
        }
    }

    @GetMapping("/{leagueId}")
    public ResponseEntity<ResponseDto<AdminProleagueResponseDto>> getProleague(@PathVariable Long leagueId) {
        try {
            return respond(ResponseDto.success(adminProleagueService.getProleague(leagueId)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            log.warn("Failed to get admin proleague. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("프로리그 조회에 실패했습니다."));
        }
    }

    @GetMapping("/{leagueId}/drafts")
    public ResponseEntity<ResponseDto<List<DraftSessionSummaryResponseDto>>> listLinkedDrafts(@PathVariable Long leagueId) {
        try {
            return respond(ResponseDto.success(adminProleagueService.listLinkedDrafts(leagueId)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (Exception e) {
            log.warn("Failed to list linked proleague drafts. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("Linked draft list lookup failed."));
        }
    }

    @PutMapping("/{leagueId}")
    public ResponseEntity<ResponseDto<AdminProleagueResponseDto>> updateProleague(
            @PathVariable Long leagueId,
            @RequestBody AdminProleagueCreateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequired();
            }
            Long adminUserId = userDetails.getUserPk();
            return respond(ResponseDto.success(adminProleagueService.updateProleague(leagueId, request, adminUserId)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to update admin proleague. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("프로리그 수정에 실패했습니다."));
        }
    }

    @GetMapping
    public ResponseEntity<ResponseDto<AdminProleaguePageResponseDto>> listProleagues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        try {
            return respond(ResponseDto.success(adminProleagueService.listProleagues(page, size, keyword, status)));
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (Exception e) {
            log.warn("Failed to list admin proleagues.", e);
            return respond(ResponseDto.fail("프로리그 목록 조회에 실패했습니다."));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ResponseDto<AdminProleagueHistoryPageResponseDto>> listHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        try {
            return respond(ResponseDto.success(adminProleagueService.listHistory(page, size, keyword, fromDate, toDate)));
        } catch (Exception e) {
            log.warn("Failed to list admin proleague history.", e);
            return respond(ResponseDto.fail("프로리그 이력 조회에 실패했습니다."));
        }
    }

    @GetMapping("/history/{leagueId}")
    public ResponseEntity<ResponseDto<AdminProleagueHistoryResponseDto>> getHistory(@PathVariable Long leagueId) {
        try {
            return respond(ResponseDto.success(adminProleagueService.getHistory(leagueId)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to get admin proleague history. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("?꾨줈由ш렇 ?대젰 ?곸꽭 議고쉶???ㅽ뙣?덉뒿?덈떎."));
        }
    }

    @PostMapping("/history/delete")
    public ResponseEntity<ResponseDto<AdminProleagueDeleteResponseDto>> deleteProleagueHistories(
            @RequestBody AdminProleagueDeleteRequestDto request
    ) {
        try {
            List<Long> leagueIds = request == null ? List.of() : request.getLeagueIds();
            return respond(ResponseDto.success(adminProleagueService.deleteProleagueHistories(leagueIds)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to delete admin proleague histories.", e);
            return respond(ResponseDto.fail("?꾨줈由ш렇 ?대젰 ??젣???ㅽ뙣?덉뒿?덈떎."));
        }
    }

    @PostMapping("/{leagueId}/finish")
    public ResponseEntity<ResponseDto<AdminProleagueHistoryResponseDto>> finishProleague(
            @PathVariable Long leagueId,
            @RequestBody AdminProleagueFinishRequestDto request
    ) {
        try {
            return respond(ResponseDto.success(adminProleagueService.finishProleague(leagueId, request)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (Exception e) {
            log.warn("Failed to finish admin proleague. leagueId={}", leagueId, e);
            return respond(ResponseDto.fail("?꾨줈由ш렇 醫낅즺???ㅽ뙣?덉뒿?덈떎."));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<ResponseDto<AdminProleagueDeleteResponseDto>> deleteProleagues(
            @RequestBody AdminProleagueDeleteRequestDto request
    ) {
        try {
            List<Long> leagueIds = request == null ? List.of() : request.getLeagueIds();
            return respond(ResponseDto.success(adminProleagueService.deleteProleagues(leagueIds)));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (IllegalArgumentException e) {
            return validationFailed(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (Exception e) {
            log.warn("Failed to delete admin proleagues.", e);
            return respond(ResponseDto.fail("프로리그 삭제에 실패했습니다."));
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
