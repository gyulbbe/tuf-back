package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveEventType;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSnapshotResponseDto;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftPickEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftTeamEntity;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftCandidateRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftPickRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftSessionRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftTeamRepository;
import io.github.gyulbbe.rpsdraft.ws.RpsDraftEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class RpsDraftLiveCommandService {

    private final RpsDraftSessionRepository rpsDraftSessionRepository;
    private final RpsDraftTeamRepository rpsDraftTeamRepository;
    private final RpsDraftCandidateRepository rpsDraftCandidateRepository;
    private final RpsDraftPickRepository rpsDraftPickRepository;
    private final RpsDraftPermissionService rpsDraftPermissionService;
    private final RpsDraftSnapshotService rpsDraftSnapshotService;
    private final RpsDraftEventPublisher rpsDraftEventPublisher;

    public RpsDraftLiveSnapshotResponseDto submitRps(Long sessionId, String choice, RpsDraftActor actor) {
        rpsDraftPermissionService.assertAuthenticated(actor);
        validateChoice(choice);

        RpsDraftSessionEntity session = loadSessionForUpdate(sessionId);
        if (!RpsDraftSessionEntity.STATUS_RPS_PENDING.equals(session.getStatus())) {
            throw new IllegalArgumentException("RPS can only be submitted while the session is waiting for RPS.");
        }

        List<RpsDraftTeamEntity> teams = loadSessionTeams(sessionId);
        RpsDraftTeamEntity actorTeam = resolvePickerTeam(sessionId, actor.userPk());
        if (session.hasChoice(actorTeam.getDisplayOrder())) {
            throw new IllegalArgumentException("This team has already submitted its RPS choice for the round.");
        }

        session.submitChoice(actorTeam.getDisplayOrder(), choice);

        RpsDraftLiveEventType eventType = RpsDraftLiveEventType.RPS_SUBMITTED;
        String message = "RPS submitted.";
        String roundResult = null;

        if (session.getTeam1RpsChoice() != null && session.getTeam2RpsChoice() != null) {
            roundResult = evaluateRound(session.getTeam1RpsChoice(), session.getTeam2RpsChoice());
            eventType = RpsDraftLiveEventType.RPS_RESOLVED;

            RpsDraftTeamEntity team1 = teams.stream()
                    .filter(team -> team.getDisplayOrder() == 1)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Team 1 could not be found."));
            RpsDraftTeamEntity team2 = teams.stream()
                    .filter(team -> team.getDisplayOrder() == 2)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Team 2 could not be found."));

            if (RpsDraftSessionEntity.RPS_RESULT_DRAW.equals(roundResult)) {
                session.resetRpsRound();
                message = "RPS round ended in a draw.";
            } else if (RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN.equals(roundResult)) {
                session.resolveRpsRound(team1.getId(), team2.getId(), roundResult);
                message = "Team 1 won the RPS round.";
            } else {
                session.resolveRpsRound(team2.getId(), team1.getId(), roundResult);
                message = "Team 2 won the RPS round.";
            }
        }

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(sessionId, actor);
        rpsDraftEventPublisher.publishAfterCommit(sessionId, eventType, actor, message, roundResult);
        return snapshot;
    }

    public RpsDraftLiveSnapshotResponseDto pick(Long sessionId, Long candidateId, RpsDraftActor actor) {
        rpsDraftPermissionService.assertAuthenticated(actor);
        if (candidateId == null) {
            throw new IllegalArgumentException("Candidate id is required.");
        }

        RpsDraftSessionEntity session = loadSessionForUpdate(sessionId);
        if (!RpsDraftSessionEntity.STATUS_PICKING.equals(session.getStatus())) {
            throw new IllegalArgumentException("Candidates can only be picked while the session is in PICKING status.");
        }

        RpsDraftTeamEntity actorTeam = resolvePickerTeam(sessionId, actor.userPk());
        if (!actorTeam.getId().equals(session.getCurrentDraftTeamId())) {
            throw new IllegalArgumentException("Only the current team's picker can make this pick.");
        }

        RpsDraftCandidateEntity candidate = rpsDraftCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("RPS draft candidate could not be found."));
        assertCandidatePickable(candidate, sessionId, candidateId);

        LocalDateTime now = LocalDateTime.now();
        savePick(
                sessionId,
                session.getCurrentPickNo(),
                actorTeam.getId(),
                candidateId,
                actor.userPk(),
                now
        );
        candidate.markPicked(actorTeam.getId(), now);

        long waitingCandidates = rpsDraftCandidateRepository.countByRpsDraftSessionIdAndStatus(
                sessionId,
                RpsDraftCandidateEntity.STATUS_WAITING
        );

        RpsDraftLiveEventType eventType;
        String message;
        if (waitingCandidates <= 0) {
            session.finish(now);
            eventType = RpsDraftLiveEventType.SESSION_FINISHED;
            message = "RPS draft session finished.";
        } else if (waitingCandidates == 1 && session.getPendingDraftTeamId() != null) {
            assignFinalCandidateToPendingTeam(sessionId, session, now);
            eventType = RpsDraftLiveEventType.SESSION_FINISHED;
            message = "RPS draft session finished with an automatic final assignment.";
        } else if (session.getPendingDraftTeamId() != null) {
            session.advanceToPendingPick(session.getCurrentPickNo() + 1);
            eventType = RpsDraftLiveEventType.TURN_CHANGED;
            message = "Turn changed to the pending team.";
        } else {
            session.prepareNextRpsRound(session.getCurrentPickNo() + 1);
            eventType = RpsDraftLiveEventType.PICK_COMPLETED;
            message = "Pick completed. Waiting for the next RPS round.";
        }

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(sessionId, actor);
        rpsDraftEventPublisher.publishAfterCommit(sessionId, eventType, actor, message, null);
        return snapshot;
    }

    private void savePick(
            Long sessionId,
            Integer pickNo,
            Long rpsDraftTeamId,
            Long candidateId,
            Long pickedByUserId,
            LocalDateTime pickedAt
    ) {
        rpsDraftPickRepository.save(
                RpsDraftPickEntity.builder()
                        .rpsDraftSessionId(sessionId)
                        .pickNo(Long.valueOf(pickNo))
                        .rpsDraftTeamId(rpsDraftTeamId)
                        .candidateId(candidateId)
                        .pickedByUserId(pickedByUserId)
                        .pickedAt(pickedAt)
                        .build()
        );
    }

    private void assignFinalCandidateToPendingTeam(
            Long sessionId,
            RpsDraftSessionEntity session,
            LocalDateTime now
    ) {
        RpsDraftTeamEntity pendingTeam = rpsDraftTeamRepository.findById(session.getPendingDraftTeamId())
                .filter(team -> sessionId.equals(team.getRpsDraftSessionId()))
                .orElseThrow(() -> new IllegalArgumentException("Pending RPS draft team could not be found."));

        if (pendingTeam.getPickerUserId() == null) {
            throw new IllegalArgumentException("Pending RPS draft team picker could not be found.");
        }

        RpsDraftCandidateEntity finalCandidate = findSingleWaitingCandidate(sessionId);
        int finalPickNo = session.getCurrentPickNo() + 1;

        savePick(
                sessionId,
                finalPickNo,
                pendingTeam.getId(),
                finalCandidate.getId(),
                pendingTeam.getPickerUserId(),
                now
        );
        finalCandidate.markPicked(pendingTeam.getId(), now);
        session.advanceToPendingPick(finalPickNo);
        session.finish(now);
    }

    private RpsDraftCandidateEntity findSingleWaitingCandidate(Long sessionId) {
        return rpsDraftCandidateRepository.findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(sessionId).stream()
                .filter(candidate -> RpsDraftCandidateEntity.STATUS_WAITING.equals(candidate.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Waiting RPS draft candidate could not be found."));
    }

    public RpsDraftLiveSnapshotResponseDto finishSession(Long sessionId, RpsDraftActor actor) {
        RpsDraftSessionEntity session = loadSessionForUpdate(sessionId);
        rpsDraftPermissionService.assertOwner(session, actor);

        if (RpsDraftSessionEntity.STATUS_FINISHED.equals(session.getStatus())) {
            throw new IllegalArgumentException("Session is already finished.");
        }

        session.finish(LocalDateTime.now());

        RpsDraftLiveSnapshotResponseDto snapshot = rpsDraftSnapshotService.getSnapshot(sessionId, actor);
        rpsDraftEventPublisher.publishAfterCommit(
                sessionId,
                RpsDraftLiveEventType.SESSION_FINISHED,
                actor,
                "RPS draft session finished.",
                null
        );
        return snapshot;
    }

    private RpsDraftSessionEntity loadSessionForUpdate(Long sessionId) {
        return rpsDraftSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("RPS draft session could not be found."));
    }

    private List<RpsDraftTeamEntity> loadSessionTeams(Long sessionId) {
        return rpsDraftTeamRepository.findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(sessionId);
    }

    private RpsDraftTeamEntity resolvePickerTeam(Long sessionId, Long userPk) {
        return rpsDraftPermissionService.findPickerTeam(sessionId, userPk)
                .orElseThrow(() -> new IllegalArgumentException("Only a picker assigned to this session can perform this action."));
    }

    private void validateChoice(String choice) {
        if (!RpsDraftSessionEntity.RPS_ROCK.equals(choice)
                && !RpsDraftSessionEntity.RPS_PAPER.equals(choice)
                && !RpsDraftSessionEntity.RPS_SCISSORS.equals(choice)) {
            throw new IllegalArgumentException("RPS choice must be ROCK, PAPER, or SCISSORS.");
        }
    }

    private String evaluateRound(String team1Choice, String team2Choice) {
        if (team1Choice.equals(team2Choice)) {
            return RpsDraftSessionEntity.RPS_RESULT_DRAW;
        }
        if (RpsDraftSessionEntity.RPS_ROCK.equals(team1Choice) && RpsDraftSessionEntity.RPS_SCISSORS.equals(team2Choice)) {
            return RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN;
        }
        if (RpsDraftSessionEntity.RPS_PAPER.equals(team1Choice) && RpsDraftSessionEntity.RPS_ROCK.equals(team2Choice)) {
            return RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN;
        }
        if (RpsDraftSessionEntity.RPS_SCISSORS.equals(team1Choice) && RpsDraftSessionEntity.RPS_PAPER.equals(team2Choice)) {
            return RpsDraftSessionEntity.RPS_RESULT_TEAM1_WIN;
        }
        return RpsDraftSessionEntity.RPS_RESULT_TEAM2_WIN;
    }

    private void assertCandidatePickable(RpsDraftCandidateEntity candidate, Long sessionId, Long candidateId) {
        if (!sessionId.equals(candidate.getRpsDraftSessionId())) {
            throw new IllegalArgumentException("RPS draft candidate could not be found.");
        }
        if (rpsDraftPickRepository.existsByRpsDraftSessionIdAndCandidateId(sessionId, candidateId)) {
            throw new IllegalArgumentException("Candidate has already been picked.");
        }
        if (!RpsDraftCandidateEntity.STATUS_WAITING.equals(candidate.getStatus())) {
            throw new IllegalArgumentException("Only WAITING candidates can be picked.");
        }
    }
}
