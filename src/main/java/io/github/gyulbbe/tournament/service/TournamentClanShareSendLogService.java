package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogSummaryResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentClanShareSendLogEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentClanShareSendLogRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TournamentClanShareSendLogService {

    private static final int MESSAGE_MAX_LENGTH = 500;

    private final TournamentRepository tournamentRepository;
    private final TournamentStageRepository stageRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentClanShareSendLogRepository logRepository;

    @Transactional(readOnly = true)
    public TournamentClanShareSendLogSummaryResponseDto getSummary(Long tournamentId) {
        requireTournament(tournamentId);
        long totalCount = logRepository.countByTournamentId(tournamentId);

        return TournamentClanShareSendLogSummaryResponseDto.builder()
                .hasHistory(totalCount > 0)
                .totalCount(totalCount)
                .latestSentAt(logRepository.findFirstByTournamentIdOrderByRegDateDescIdDesc(tournamentId)
                        .map(TournamentClanShareSendLogEntity::getRegDate)
                        .orElse(null))
                .build();
    }

    @Transactional
    public TournamentClanShareSendLogResponseDto createLog(
            TournamentClanShareSendLogRequestDto request,
            Long requestedByUserId
    ) {
        if (requestedByUserId == null) {
            throw invalid("requestedByUserId is required.");
        }
        validateRequest(request);
        validateMatchInTournament(request.getTournamentId(), request.getMatchId());

        TournamentClanShareSendLogEntity saved = logRepository.save(TournamentClanShareSendLogEntity.builder()
                .tournamentId(request.getTournamentId())
                .matchId(request.getMatchId())
                .sendGroupId(trimRequired(request.getSendGroupId(), "sendGroupId"))
                .player1(trimRequired(request.getPlayer1(), "player1"))
                .player2(trimRequired(request.getPlayer2(), "player2"))
                .winner(trimRequired(request.getWinner(), "winner"))
                .loser(trimRequired(request.getLoser(), "loser"))
                .mapName(trimRequired(request.getMapName(), "mapName"))
                .matchType(trimRequired(request.getMatchType(), "matchType"))
                .matchName(trimRequired(request.getMatchName(), "matchName"))
                .playedDate(trimRequired(request.getPlayedDate(), "playedDate"))
                .eloStatus(trimRequired(request.getEloStatus(), "eloStatus"))
                .eloMessage(trimOptional(request.getEloMessage()))
                .sheetStatus(trimRequired(request.getSheetStatus(), "sheetStatus"))
                .sheetMessage(trimOptional(request.getSheetMessage()))
                .requestedByUserId(requestedByUserId)
                .build());

        return toResponse(saved);
    }

    private void validateRequest(TournamentClanShareSendLogRequestDto request) {
        if (request == null) {
            throw invalid("Request body is required.");
        }
        if (request.getTournamentId() == null) {
            throw invalid("tournamentId is required.");
        }
        if (request.getMatchId() == null) {
            throw invalid("matchId is required.");
        }
        requireValidStatus(request.getEloStatus(), "eloStatus");
        requireValidStatus(request.getSheetStatus(), "sheetStatus");
        trimRequired(request.getSendGroupId(), "sendGroupId");
        trimRequired(request.getPlayer1(), "player1");
        trimRequired(request.getPlayer2(), "player2");
        trimRequired(request.getWinner(), "winner");
        trimRequired(request.getLoser(), "loser");
        trimRequired(request.getMapName(), "mapName");
        trimRequired(request.getMatchType(), "matchType");
        trimRequired(request.getMatchName(), "matchName");
        trimRequired(request.getPlayedDate(), "playedDate");
    }

    private void validateMatchInTournament(Long tournamentId, Long matchId) {
        requireTournament(tournamentId);
        TournamentMatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> notFound("Match not found."));
        TournamentStageEntity stage = stageRepository.findById(match.getStageId())
                .orElseThrow(() -> notFound("Tournament stage not found."));
        if (!Objects.equals(stage.getTournamentId(), tournamentId)) {
            throw notFound("Match not found in tournament.");
        }
    }

    private void requireTournament(Long tournamentId) {
        if (tournamentId == null) {
            throw invalid("tournamentId is required.");
        }
        if (!tournamentRepository.existsById(tournamentId)) {
            throw notFound("Tournament not found.");
        }
    }

    private void requireValidStatus(String status, String fieldName) {
        String normalized = trimRequired(status, fieldName);
        if (!TournamentClanShareSendLogEntity.STATUS_SUCCESS.equals(normalized)
                && !TournamentClanShareSendLogEntity.STATUS_FAILED.equals(normalized)) {
            throw invalid(fieldName + " must be SUCCESS or FAILED.");
        }
    }

    private String trimRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(fieldName + " is required.");
        }
        return value.trim();
    }

    private String trimOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().substring(0, Math.min(value.trim().length(), MESSAGE_MAX_LENGTH));
    }

    private TournamentClanShareSendLogResponseDto toResponse(TournamentClanShareSendLogEntity entity) {
        return TournamentClanShareSendLogResponseDto.builder()
                .id(entity.getId())
                .tournamentId(entity.getTournamentId())
                .matchId(entity.getMatchId())
                .sendGroupId(entity.getSendGroupId())
                .player1(entity.getPlayer1())
                .player2(entity.getPlayer2())
                .winner(entity.getWinner())
                .loser(entity.getLoser())
                .mapName(entity.getMapName())
                .matchType(entity.getMatchType())
                .matchName(entity.getMatchName())
                .playedDate(entity.getPlayedDate())
                .eloStatus(entity.getEloStatus())
                .eloMessage(entity.getEloMessage())
                .sheetStatus(entity.getSheetStatus())
                .sheetMessage(entity.getSheetMessage())
                .requestedByUserId(entity.getRequestedByUserId())
                .regDate(entity.getRegDate())
                .build();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private NoSuchElementException notFound(String message) {
        return new NoSuchElementException(message);
    }
}
