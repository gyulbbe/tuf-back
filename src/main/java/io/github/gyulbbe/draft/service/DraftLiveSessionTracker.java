package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class DraftLiveSessionTracker {

    private static final String LIVE = "LIVE";

    private final DraftSessionRepository draftSessionRepository;
    private final AtomicBoolean liveSessionPresent = new AtomicBoolean(false);

    @PostConstruct
    void initialize() {
        synchronizeWithDatabase();
    }

    public boolean hasLiveSession() {
        return liveSessionPresent.get();
    }

    public void markLiveSessionPresentAfterCommit() {
        runAfterCommit(() -> liveSessionPresent.set(true));
    }

    public void refreshAfterCommit() {
        runAfterCommit(this::synchronizeWithDatabase);
    }

    void synchronizeWithDatabase() {
        liveSessionPresent.set(draftSessionRepository.existsByStatus(LIVE));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }

        action.run();
    }
}
