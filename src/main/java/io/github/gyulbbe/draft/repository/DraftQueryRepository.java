package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.dto.*;

import java.util.List;
import java.util.Optional;

public interface DraftQueryRepository {
    Optional<DraftSessionSummaryResponseDto> findSessionSummary(Long sessionId);

    List<DraftSessionSummaryResponseDto> findSessionSummaries();

    Optional<DraftTeamResponseDto> findTeam(Long teamId);

    List<DraftTeamResponseDto> findTeamsBySessionId(Long sessionId);

    List<DraftTeamOperatorResponseDto> findOperatorsByTeamIds(List<Long> teamIds);

    List<DraftCandidateResponseDto> findCandidatesBySessionId(Long sessionId);

    Optional<DraftCandidateResponseDto> findCandidate(Long sessionId, Long candidateUserId);

    List<DraftOrderResponseDto> findOrdersBySessionId(Long sessionId);

    Optional<DraftOrderResponseDto> findOrder(Long sessionId, Long pickNo);

    List<DraftPickResponseDto> findPicksBySessionId(Long sessionId);

    Optional<DraftPickResponseDto> findPick(Long sessionId, Long pickNo);
}
