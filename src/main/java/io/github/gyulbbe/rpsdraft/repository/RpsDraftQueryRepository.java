package io.github.gyulbbe.rpsdraft.repository;

import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftPickResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionQueryDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftTeamResponseDto;

import java.util.List;
import java.util.Optional;

public interface RpsDraftQueryRepository {
    Optional<RpsDraftSessionQueryDto> findSession(Long sessionId);

    List<RpsDraftSessionQueryDto> findSessions();

    List<RpsDraftTeamResponseDto> findTeamsBySessionId(Long sessionId);

    Optional<RpsDraftTeamResponseDto> findTeam(Long teamId);

    List<RpsDraftCandidateResponseDto> findCandidatesBySessionId(Long sessionId);

    Optional<RpsDraftCandidateResponseDto> findCandidate(Long sessionId, Long candidateUserId);

    List<RpsDraftPickResponseDto> findPicksBySessionId(Long sessionId);
}
