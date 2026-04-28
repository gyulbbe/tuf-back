package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftLiveSnapshotResponseDto;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftTimeoutScheduler {

    private static final AuthActor SYSTEM_ACTOR = new AuthActor(null, "system", "ROLE_SYSTEM");
    private static final String LIVE = "LIVE";
    private static final String FINISHED = "FINISHED";
    private static final String TIMEOUT_REASON = "timeout";

    private final DraftLiveSessionTracker draftLiveSessionTracker;
    private final DraftSessionRepository draftSessionRepository;
    private final DraftLiveCommandService draftLiveCommandService;
    private final ConcurrentHashMap<Long, TimeoutSkipState> timeoutSkipStates = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 1000)
    public void processTimeouts() {
        if (!draftLiveSessionTracker.hasLiveSession()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<DraftSessionEntity> overdueSessions = draftSessionRepository
                .findAllByStatusAndDeadlineAtLessThanEqual(LIVE, now);

        for (DraftSessionEntity overdueSession : overdueSessions) {
            resetStateIfTurnChanged(overdueSession);
            try {
                DraftLiveSnapshotResponseDto snapshot = draftLiveCommandService.forceSkip(
                        overdueSession.getId(),
                        SYSTEM_ACTOR,
                        TIMEOUT_REASON
                );
                handleSuccessfulTimeoutSkip(overdueSession.getId(), snapshot);
            } catch (IllegalArgumentException e) {
                pauseAfterDomainFailure(overdueSession.getId(), e);
            } catch (Exception e) {
                log.debug("draft timeout skip ignored. sessionId={}, reason={}", overdueSession.getId(), e.getMessage());
            }
        }
    }

    private void resetStateIfTurnChanged(DraftSessionEntity overdueSession) {
        Long sessionId = overdueSession.getId();
        TimeoutSkipState state = timeoutSkipStates.get(sessionId);
        if (state == null || state.lastAutoAdvancedPickNo() == null) {
            return;
        }

        Long currentPickNo = overdueSession.getCurrentPickNo() == null
                ? null
                : overdueSession.getCurrentPickNo().longValue();
        if (!Objects.equals(state.lastAutoAdvancedPickNo(), currentPickNo)) {
            timeoutSkipStates.remove(sessionId);
        }
    }

    private void handleSuccessfulTimeoutSkip(Long sessionId, DraftLiveSnapshotResponseDto snapshot) {
        if (snapshot == null || snapshot.getSession() == null) {
            timeoutSkipStates.remove(sessionId);
            return;
        }

        String status = snapshot.getSession().getStatus();
        if (FINISHED.equals(status) || !LIVE.equals(status)) {
            timeoutSkipStates.remove(sessionId);
            return;
        }

        TimeoutSkipState state = timeoutSkipStates.compute(sessionId, (key, existing) -> {
            int nextCount = existing == null ? 1 : existing.consecutiveTimeoutSkips() + 1;
            Long currentPickNo = snapshot.getSession().getCurrentPickNo() == null
                    ? null
                    : snapshot.getSession().getCurrentPickNo().longValue();
            return new TimeoutSkipState(nextCount, currentPickNo);
        });

        if (state.consecutiveTimeoutSkips() >= timeoutGuardThreshold(snapshot)) {
            try {
                draftLiveCommandService.pauseAfterTimeoutGuard(sessionId, SYSTEM_ACTOR);
            } finally {
                timeoutSkipStates.remove(sessionId);
            }
        }
    }

    private int timeoutGuardThreshold(DraftLiveSnapshotResponseDto snapshot) {
        Integer teamCount = snapshot.getSession().getTeamCount();
        if (teamCount == null || teamCount <= 0) {
            return 1;
        }
        return teamCount * 2;
    }

    private void pauseAfterDomainFailure(Long sessionId, IllegalArgumentException failure) {
        try {
            draftLiveCommandService.pauseAfterTimeoutGuard(sessionId, SYSTEM_ACTOR);
        } catch (Exception pauseFailure) {
            log.debug(
                    "draft timeout guard pause ignored. sessionId={}, skipReason={}, pauseReason={}",
                    sessionId,
                    failure.getMessage(),
                    pauseFailure.getMessage()
            );
        } finally {
            timeoutSkipStates.remove(sessionId);
        }
    }

    private record TimeoutSkipState(
            int consecutiveTimeoutSkips,
            Long lastAutoAdvancedPickNo
    ) {
    }
}
