package io.github.gyulbbe.tournament.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.tournament.dto.TournamentCreateRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentDeleteRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentPageResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionRejectRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentScoreSubmissionResponseDto;
import io.github.gyulbbe.tournament.service.TournamentCreationService;
import io.github.gyulbbe.tournament.service.TournamentMatchScoreSubmissionService;
import io.github.gyulbbe.tournament.service.TournamentService;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;
    private final TournamentCreationService tournamentCreationService;
    private final TournamentMatchScoreSubmissionService scoreSubmissionService;

    @GetMapping
    public ResponseEntity<ResponseDto<TournamentPageResponseDto>> listTournaments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        return respond(tournamentService.listPublicTournaments(page, size, keyword));
    }

    @GetMapping("/{tournamentId}")
    public ResponseEntity<ResponseDto<TournamentDetailResponseDto>> getTournament(@PathVariable Long tournamentId) {
        return respond(tournamentService.getPublicTournament(tournamentId));
    }

    @PostMapping
    public ResponseEntity<ResponseDto<TournamentDetailResponseDto>> createTournament(
            @RequestBody TournamentCreateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return respond(ResponseDto.fail(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Authentication is required.",
                        ApiErrorCode.AUTH_REQUIRED
                ));
            }
            TournamentDetailResponseDto response = tournamentCreationService.createTournament(request, userDetails.getUserPk());
            return respond(ResponseDto.success(response));
        } catch (IllegalArgumentException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(),
                    ApiErrorCode.VALIDATION_FAILED
            ));
        } catch (Exception e) {
            log.warn("Failed to create tournament.", e);
            return respond(ResponseDto.fail("Failed to create tournament."));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<ResponseDto<Void>> deleteTournaments(
            @RequestBody TournamentDeleteRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequiredResponse();
            }
            if (!isAdmin(userDetails)) {
                return respond(ResponseDto.fail(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Admin permission is required.",
                        ApiErrorCode.AUTH_FORBIDDEN
                ));
            }
            List<Long> tournamentIds = request == null ? List.of() : request.getTournamentIds();
            return respond(tournamentService.deleteTournaments(tournamentIds));
        } catch (NoSuchElementException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(),
                    ApiErrorCode.RESOURCE_NOT_FOUND
            ));
        } catch (IllegalArgumentException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(),
                    ApiErrorCode.VALIDATION_FAILED
            ));
        } catch (Exception e) {
            log.warn("Failed to delete tournaments.", e);
            return respond(ResponseDto.fail("Failed to delete tournaments."));
        }
    }

    @PostMapping("/{tournamentId}/matches/{matchId}/score-submissions")
    public ResponseEntity<ResponseDto<TournamentScoreSubmissionResponseDto>> submitScore(
            @PathVariable Long tournamentId,
            @PathVariable Long matchId,
            @RequestBody TournamentScoreSubmissionRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequiredResponse();
            }
            TournamentScoreSubmissionResponseDto response = scoreSubmissionService.submitScore(
                    tournamentId,
                    matchId,
                    request,
                    userDetails.getUserPk(),
                    resolveRole(userDetails)
            );
            return respond(ResponseDto.success(response));
        } catch (NoSuchElementException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(),
                    ApiErrorCode.RESOURCE_NOT_FOUND
            ));
        } catch (AccessDeniedException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_FORBIDDEN,
                    e.getMessage(),
                    ApiErrorCode.AUTH_FORBIDDEN
            ));
        } catch (IllegalArgumentException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(),
                    ApiErrorCode.VALIDATION_FAILED
            ));
        } catch (Exception e) {
            log.warn("Failed to submit tournament match score. tournamentId={}, matchId={}", tournamentId, matchId, e);
            return respond(ResponseDto.fail("Failed to submit tournament match score."));
        }
    }

    @GetMapping("/{tournamentId}/matches/{matchId}/score-submissions")
    public ResponseEntity<ResponseDto<List<TournamentScoreSubmissionResponseDto>>> listScoreSubmissions(
            @PathVariable Long tournamentId,
            @PathVariable Long matchId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequiredResponse();
            }
            List<TournamentScoreSubmissionResponseDto> response = scoreSubmissionService.listSubmissions(
                    tournamentId,
                    matchId,
                    userDetails.getUserPk(),
                    resolveRole(userDetails)
            );
            return respond(ResponseDto.success(response));
        } catch (NoSuchElementException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(),
                    ApiErrorCode.RESOURCE_NOT_FOUND
            ));
        } catch (AccessDeniedException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_FORBIDDEN,
                    e.getMessage(),
                    ApiErrorCode.AUTH_FORBIDDEN
            ));
        } catch (IllegalArgumentException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(),
                    ApiErrorCode.VALIDATION_FAILED
            ));
        } catch (Exception e) {
            log.warn("Failed to list tournament match score submissions. tournamentId={}, matchId={}", tournamentId, matchId, e);
            return respond(ResponseDto.fail("Failed to list tournament match score submissions."));
        }
    }

    @PostMapping("/{tournamentId}/matches/{matchId}/score-submissions/{submissionId}/approve")
    public ResponseEntity<ResponseDto<TournamentDetailResponseDto>> approveScoreSubmission(
            @PathVariable Long tournamentId,
            @PathVariable Long matchId,
            @PathVariable Long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequiredResponse();
            }
            TournamentDetailResponseDto response = scoreSubmissionService.approveSubmission(
                    tournamentId,
                    matchId,
                    submissionId,
                    userDetails.getUserPk(),
                    resolveRole(userDetails)
            );
            return respond(ResponseDto.success(response));
        } catch (NoSuchElementException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(),
                    ApiErrorCode.RESOURCE_NOT_FOUND
            ));
        } catch (AccessDeniedException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_FORBIDDEN,
                    e.getMessage(),
                    ApiErrorCode.AUTH_FORBIDDEN
            ));
        } catch (IllegalArgumentException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(),
                    ApiErrorCode.VALIDATION_FAILED
            ));
        } catch (Exception e) {
            log.warn(
                    "Failed to approve tournament match score submission. tournamentId={}, matchId={}, submissionId={}",
                    tournamentId,
                    matchId,
                    submissionId,
                    e
            );
            return respond(ResponseDto.fail("Failed to approve tournament match score submission."));
        }
    }

    @PostMapping("/{tournamentId}/matches/{matchId}/score-submissions/{submissionId}/reject")
    public ResponseEntity<ResponseDto<TournamentScoreSubmissionResponseDto>> rejectScoreSubmission(
            @PathVariable Long tournamentId,
            @PathVariable Long matchId,
            @PathVariable Long submissionId,
            @RequestBody TournamentScoreSubmissionRejectRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            if (userDetails == null || userDetails.getUserPk() == null) {
                return authRequiredResponse();
            }
            TournamentScoreSubmissionResponseDto response = scoreSubmissionService.rejectSubmission(
                    tournamentId,
                    matchId,
                    submissionId,
                    request,
                    userDetails.getUserPk(),
                    resolveRole(userDetails)
            );
            return respond(ResponseDto.success(response));
        } catch (NoSuchElementException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(),
                    ApiErrorCode.RESOURCE_NOT_FOUND
            ));
        } catch (AccessDeniedException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_FORBIDDEN,
                    e.getMessage(),
                    ApiErrorCode.AUTH_FORBIDDEN
            ));
        } catch (IllegalArgumentException e) {
            return respond(ResponseDto.fail(
                    HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(),
                    ApiErrorCode.VALIDATION_FAILED
            ));
        } catch (Exception e) {
            log.warn(
                    "Failed to reject tournament match score submission. tournamentId={}, matchId={}, submissionId={}",
                    tournamentId,
                    matchId,
                    submissionId,
                    e
            );
            return respond(ResponseDto.fail("Failed to reject tournament match score submission."));
        }
    }

    private <T> ResponseEntity<ResponseDto<T>> authRequiredResponse() {
        return respond(ResponseDto.fail(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Authentication is required.",
                ApiErrorCode.AUTH_REQUIRED
        ));
    }

    private String resolveRole(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse(null);
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role ->
                        "ROLE_ADMIN".equals(role)
                                || "ROLE_MANAGER".equals(role)
                                || "ROLE_MASTER".equals(role)
                                || "ADMIN".equals(role)
                                || "MANAGER".equals(role)
                                || "MASTER".equals(role)
                );
    }
}
