package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftLiveEventType;
import io.github.gyulbbe.draft.dto.DraftLivePreviewEndReason;
import io.github.gyulbbe.draft.dto.DraftLiveSnapshotResponseDto;
import io.github.gyulbbe.draft.entity.DraftCandidateEntity;
import io.github.gyulbbe.draft.entity.DraftCandidateId;
import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftPickEntity;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.repository.DraftCandidateRepository;
import io.github.gyulbbe.draft.repository.DraftPickRepository;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.ws.DraftEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Transactional
@RequiredArgsConstructor
public class DraftLiveCommandService {

    private static final String CANDIDATE_WAITING = "WAITING";
    private static final String MESSAGE_SESSION_STARTED = "드래프트를 시작했습니다.";
    private static final String MESSAGE_SESSION_PAUSED = "드래프트를 일시정지했습니다.";
    private static final String MESSAGE_SESSION_RESUMED = "드래프트를 재개했습니다.";
    private static final String MESSAGE_TIMER_EXTENDED = "제한 시간을 연장했습니다.";
    private static final String MESSAGE_PICK_COMPLETED = "지명을 완료했습니다.";
    private static final String MESSAGE_FINAL_PICK_FINISHED = "마지막 지명이 완료되어 드래프트가 종료되었습니다.";
    private static final String MESSAGE_SESSION_FINISHED = "드래프트가 종료되었습니다.";
    private static final String MESSAGE_PICK_SKIPPED = "현재 턴을 스킵했습니다.";

    private final DraftSessionRepository draftSessionRepository;
    private final DraftCandidateRepository draftCandidateRepository;
    private final DraftPickRepository draftPickRepository;
    private final DraftOrderPatternService draftOrderPatternService;
    private final DraftPermissionService draftPermissionService;
    private final DraftSnapshotService draftSnapshotService;
    private final DraftEventPublisher draftEventPublisher;
    private final DraftLiveSessionTracker draftLiveSessionTracker;
    private final DraftLivePreviewRelayService draftLivePreviewRelayService;

    public DraftLiveSnapshotResponseDto startSession(Long sessionId, AuthActor actor) {
        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        draftPermissionService.assertAdmin(actor);

        if (!"READY".equals(session.getStatus())) {
            throw new IllegalArgumentException("Only READY sessions can be started.");
        }

        LocalDateTime now = LocalDateTime.now();
        DraftOrderEntity firstOrder = requireOrder(sessionId, 1L);
        session.start(firstOrder.getDraftTeamId(), now, now.plusSeconds(session.getPickTimeSeconds()));

        draftLiveSessionTracker.markLiveSessionPresentAfterCommit();

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_STARTED, actor, MESSAGE_SESSION_STARTED);
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto pauseSession(Long sessionId, AuthActor actor) {
        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        draftPermissionService.assertAdmin(actor);
        assertLiveSession(session);

        session.pause();
        draftLiveSessionTracker.refreshAfterCommit();
        draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.SESSION_PAUSED);

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_PAUSED, actor, MESSAGE_SESSION_PAUSED);
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto resumeSession(Long sessionId, AuthActor actor, Integer seconds) {
        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        draftPermissionService.assertAdmin(actor);

        if (!"PAUSED".equals(session.getStatus())) {
            throw new IllegalArgumentException("Only PAUSED sessions can be resumed.");
        }

        DraftOrderEntity currentOrder = requireCurrentOrder(session);
        int resumeSeconds = seconds != null ? seconds : session.getPickTimeSeconds();
        if (resumeSeconds <= 0) {
            throw new IllegalArgumentException("Resume seconds must be greater than 0.");
        }

        session.synchronizeCurrentDraftTeam(currentOrder.getDraftTeamId());
        session.resume(LocalDateTime.now().plusSeconds(resumeSeconds));
        draftLiveSessionTracker.markLiveSessionPresentAfterCommit();

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_RESUMED, actor, MESSAGE_SESSION_RESUMED);
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto extendTime(Long sessionId, AuthActor actor, Integer seconds) {
        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        draftPermissionService.assertAdmin(actor);
        assertLiveSession(session);

        if (seconds == null || seconds <= 0) {
            throw new IllegalArgumentException("Extension seconds must be greater than 0.");
        }
        if (session.getDeadlineAt() == null) {
            throw new IllegalArgumentException("A live turn deadline does not exist.");
        }

        session.extendDeadlineAt(session.getDeadlineAt().plusSeconds(seconds));

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.TIMER_EXTENDED, actor, MESSAGE_TIMER_EXTENDED);
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto pick(Long sessionId, Long candidateUserId, AuthActor actor) {
        if (candidateUserId == null) {
            throw new IllegalArgumentException("Candidate user id is required.");
        }
        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        assertLiveSession(session);

        DraftOrderEntity currentOrder = requireCurrentOrder(session);
        Long currentDraftTeamId = synchronizeCurrentTurnWithOrder(session, currentOrder);
        if (!draftPermissionService.canPickForTeam(currentDraftTeamId, actor.userPk())) {
            throw new IllegalArgumentException("Only the picker for the current team can make a pick.");
        }
        if (!currentDraftTeamId.equals(currentOrder.getDraftTeamId())) {
            throw new IllegalArgumentException("The current draft turn is not aligned with the order.");
        }

        DraftCandidateEntity candidate = draftCandidateRepository.findById(new DraftCandidateId(sessionId, candidateUserId))
                .orElseThrow(() -> new IllegalArgumentException("Draft candidate could not be found."));
        assertCandidatePickable(candidate, sessionId, candidateUserId);

        LocalDateTime now = LocalDateTime.now();
        DraftPickEntity pick = DraftPickEntity.builder()
                .draftSessionId(sessionId)
                .pickNo(currentOrder.getPickNo())
                .draftTeamId(currentDraftTeamId)
                .candidateUserId(candidateUserId)
                .pickedByUserId(actor.userPk())
                .pickedAt(now)
                .build();
        draftPickRepository.save(pick);

        candidate.markPicked(currentDraftTeamId, now);
        advanceTurnOrFinish(session, now);

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        if ("FINISHED".equals(snapshot.getSession().getStatus())) {
            draftLiveSessionTracker.refreshAfterCommit();
            draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.SESSION_FINISHED);
            publishAfterCommit(sessionId, DraftLiveEventType.SESSION_FINISHED, actor, MESSAGE_FINAL_PICK_FINISHED);
        } else {
            draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.TURN_CHANGED);
            publishAfterCommit(sessionId, DraftLiveEventType.PICK_COMPLETED, actor, MESSAGE_PICK_COMPLETED);
        }
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto forceSkip(Long sessionId, AuthActor actor, String reason) {
        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        DraftOrderEntity currentOrder = requireCurrentOrder(session);
        draftPermissionService.assertSystemOrCurrentPicker(currentOrder.getDraftTeamId(), actor);
        assertLiveSession(session);

        synchronizeCurrentTurnWithOrder(session, currentOrder);
        advanceTurnOrFinish(session, LocalDateTime.now());

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        if ("FINISHED".equals(snapshot.getSession().getStatus())) {
            draftLiveSessionTracker.refreshAfterCommit();
            draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.SESSION_FINISHED);
            publishAfterCommit(sessionId, DraftLiveEventType.SESSION_FINISHED, actor, MESSAGE_SESSION_FINISHED);
        } else {
            draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.TURN_CHANGED);
            publishAfterCommit(sessionId, DraftLiveEventType.PICK_SKIPPED, actor, MESSAGE_PICK_SKIPPED);
        }
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto finishSession(Long sessionId, AuthActor actor, String reason) {
        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        draftPermissionService.assertAdmin(actor);

        if ("FINISHED".equals(session.getStatus())) {
            throw new IllegalArgumentException("The session is already finished.");
        }

        session.finish(LocalDateTime.now());
        draftLiveSessionTracker.refreshAfterCommit();
        draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.SESSION_FINISHED);

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_FINISHED, actor, MESSAGE_SESSION_FINISHED);
        return snapshot;
    }

    private DraftSessionEntity loadSessionForUpdate(Long sessionId) {
        return draftSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Draft session could not be found."));
    }

    private void assertLiveSession(DraftSessionEntity session) {
        if (!"LIVE".equals(session.getStatus())) {
            throw new IllegalArgumentException("Only LIVE sessions can be controlled.");
        }
    }

    private void assertCandidatePickable(DraftCandidateEntity candidate, Long sessionId, Long candidateUserId) {
        if (draftPickRepository.existsByDraftSessionIdAndCandidateUserId(sessionId, candidateUserId)) {
            throw new IllegalArgumentException("Candidate has already been picked.");
        }
        if (!CANDIDATE_WAITING.equals(candidate.getStatus())) {
            throw new IllegalArgumentException("Only WAITING candidates can be picked.");
        }
    }

    private void advanceTurnOrFinish(DraftSessionEntity session, LocalDateTime now) {
        long nextPickNo = session.getCurrentPickNo() + 1L;
        if (draftCandidateRepository.countByDraftSessionIdAndStatus(session.getId(), CANDIDATE_WAITING) <= 0) {
            session.finish(now);
            return;
        }

        DraftOrderEntity nextOrder = draftOrderPatternService.getOrCreateOrder(session.getId(), nextPickNo);
        session.advanceTurn((int) nextPickNo, nextOrder.getDraftTeamId(), now.plusSeconds(session.getPickTimeSeconds()));
    }

    private long requireCurrentPickNo(DraftSessionEntity session) {
        if (session.getCurrentPickNo() == null || session.getCurrentPickNo() <= 0) {
            throw new IllegalArgumentException("Current pick number is invalid.");
        }
        return session.getCurrentPickNo().longValue();
    }

    private DraftOrderEntity requireCurrentOrder(DraftSessionEntity session) {
        return requireOrder(session.getId(), requireCurrentPickNo(session));
    }

    private DraftOrderEntity requireOrder(Long sessionId, long pickNo) {
        return draftOrderPatternService.requireExistingOrder(sessionId, pickNo);
    }

    private Long synchronizeCurrentTurnWithOrder(DraftSessionEntity session, DraftOrderEntity currentOrder) {
        Long currentDraftTeamId = currentOrder.getDraftTeamId();
        if (!Objects.equals(session.getCurrentDraftTeamId(), currentDraftTeamId)) {
            session.synchronizeCurrentDraftTeam(currentDraftTeamId);
        }
        return currentDraftTeamId;
    }

    private void publishAfterCommit(Long sessionId, DraftLiveEventType type, AuthActor actor, String message) {
        draftEventPublisher.publishAfterCommit(sessionId, type, actor, message);
    }
}
