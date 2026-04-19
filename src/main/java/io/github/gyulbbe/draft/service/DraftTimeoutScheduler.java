package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftTimeoutScheduler {

    private static final AuthActor SYSTEM_ACTOR = new AuthActor(null, "system", "ROLE_SYSTEM");

    private final DraftSessionRepository draftSessionRepository;
    private final DraftLiveCommandService draftLiveCommandService;

    @Scheduled(fixedDelay = 1000)
    public void processTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<DraftSessionEntity> overdueSessions = draftSessionRepository
                .findAllByStatusAndDeadlineAtLessThanEqual("LIVE", now);

        for (DraftSessionEntity overdueSession : overdueSessions) {
            try {
                draftLiveCommandService.forceSkip(overdueSession.getId(), SYSTEM_ACTOR, "timeout");
            } catch (Exception e) {
                log.debug("draft timeout skip ignored. sessionId={}, reason={}", overdueSession.getId(), e.getMessage());
            }
        }
    }
}
