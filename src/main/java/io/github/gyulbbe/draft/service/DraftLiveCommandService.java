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
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
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

    private final DraftSessionRepository draftSessionRepository;
    private final DraftOrderRepository draftOrderRepository;
    private final DraftCandidateRepository draftCandidateRepository;
    private final DraftPickRepository draftPickRepository;
    private final DraftPermissionService draftPermissionService;
    private final DraftSnapshotService draftSnapshotService;
    private final DraftEventPublisher draftEventPublisher;
    private final DraftLiveSessionTracker draftLiveSessionTracker;
    private final DraftLivePreviewRelayService draftLivePreviewRelayService;

    public DraftLiveSnapshotResponseDto startSession(Long sessionId, AuthActor actor) {
        draftPermissionService.assertAdmin(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        if (!"READY".equals(session.getStatus())) {
            throw new IllegalArgumentException("READY ?곹깭???몄뀡留??쒖옉?????덉뒿?덈떎.");
        }

        LocalDateTime now = LocalDateTime.now();
        DraftOrderEntity firstOrder = requireOrder(sessionId, 1L);
        session.start(firstOrder.getDraftTeamId(), now, now.plusSeconds(session.getPickTimeSeconds()));

        draftLiveSessionTracker.markLiveSessionPresentAfterCommit();

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_STARTED, actor, "Draft session started.");
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto pauseSession(Long sessionId, AuthActor actor) {
        draftPermissionService.assertAdmin(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        assertLiveSession(session);

        session.pause();
        draftLiveSessionTracker.refreshAfterCommit();
        draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.SESSION_PAUSED);

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_PAUSED, actor, "Draft session paused.");
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto resumeSession(Long sessionId, AuthActor actor, Integer seconds) {
        draftPermissionService.assertAdmin(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        if (!"PAUSED".equals(session.getStatus())) {
            throw new IllegalArgumentException("PAUSED ?곹깭???몄뀡留??ш컻?????덉뒿?덈떎.");
        }

        DraftOrderEntity currentOrder = requireCurrentOrder(session);
        int resumeSeconds = seconds != null ? seconds : session.getPickTimeSeconds();
        if (resumeSeconds <= 0) {
            throw new IllegalArgumentException("?ш컻 ?쒓컙? 1珥??댁긽?댁뼱???⑸땲??");
        }

        session.synchronizeCurrentDraftTeam(currentOrder.getDraftTeamId());
        session.resume(LocalDateTime.now().plusSeconds(resumeSeconds));
        draftLiveSessionTracker.markLiveSessionPresentAfterCommit();

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_RESUMED, actor, "Draft session resumed.");
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto extendTime(Long sessionId, AuthActor actor, Integer seconds) {
        draftPermissionService.assertAdmin(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        assertLiveSession(session);

        if (seconds == null || seconds <= 0) {
            throw new IllegalArgumentException("?곗옣 ?쒓컙? 1珥??댁긽?댁뼱???⑸땲??");
        }
        if (session.getDeadlineAt() == null) {
            throw new IllegalArgumentException("?꾩옱 留덇컧 ?쒓컙???놁뒿?덈떎.");
        }

        session.extendDeadlineAt(session.getDeadlineAt().plusSeconds(seconds));

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.TIMER_EXTENDED, actor, "Turn timer extended.");
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto pick(Long sessionId, Long candidateUserId, AuthActor actor) {
        if (candidateUserId == null) {
            throw new IllegalArgumentException("?꾨낫 ?좎? ID???꾩닔?낅땲??");
        }
        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("濡쒓렇?몄씠 ?꾩슂?⑸땲??");
        }

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        assertLiveSession(session);

        DraftOrderEntity currentOrder = requireCurrentOrder(session);
        Long currentDraftTeamId = synchronizeCurrentTurnWithOrder(session, currentOrder);
        if (!draftPermissionService.canPickForTeam(currentDraftTeamId, actor.userPk())) {
            throw new IllegalArgumentException("?꾩옱 ???吏?뺣맂 ?쎌빱留??좏깮?????덉뒿?덈떎.");
        }
        if (!currentDraftTeamId.equals(currentOrder.getDraftTeamId())) {
            throw new IllegalArgumentException("?몄뀡???꾩옱 ?怨??쒕옒?꾪듃 ?쒖꽌媛 ?쇱튂?섏? ?딆뒿?덈떎.");
        }

        DraftCandidateEntity candidate = draftCandidateRepository.findById(new DraftCandidateId(sessionId, candidateUserId))
                .orElseThrow(() -> new IllegalArgumentException("?쒕옒?꾪듃 ?꾨낫瑜?李얠쓣 ???놁뒿?덈떎."));
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
            publishAfterCommit(sessionId, DraftLiveEventType.SESSION_FINISHED, actor, "Final pick completed. Draft session finished.");
        } else {
            draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.TURN_CHANGED);
            publishAfterCommit(sessionId, DraftLiveEventType.PICK_COMPLETED, actor, "Pick completed.");
        }
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto forceSkip(Long sessionId, AuthActor actor, String reason) {
        draftPermissionService.assertAdminOrSystem(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        assertLiveSession(session);

        synchronizeCurrentTurnWithOrder(session, requireCurrentOrder(session));
        advanceTurnOrFinish(session, LocalDateTime.now());

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        if ("FINISHED".equals(snapshot.getSession().getStatus())) {
            draftLiveSessionTracker.refreshAfterCommit();
            draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.SESSION_FINISHED);
            publishAfterCommit(sessionId, DraftLiveEventType.SESSION_FINISHED, actor, "Draft session finished.");
        } else {
            draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.TURN_CHANGED);
            String suffix = reason == null || reason.isBlank() ? "" : ": " + reason;
            publishAfterCommit(sessionId, DraftLiveEventType.PICK_SKIPPED, actor, "Current pick skipped" + suffix + ".");
        }
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto finishSession(Long sessionId, AuthActor actor, String reason) {
        draftPermissionService.assertAdmin(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        if ("FINISHED".equals(session.getStatus())) {
            throw new IllegalArgumentException("?대? 醫낅즺???몄뀡?낅땲??");
        }

        session.finish(LocalDateTime.now());
        draftLiveSessionTracker.refreshAfterCommit();
        draftLivePreviewRelayService.clearPreviewAfterCommit(sessionId, DraftLivePreviewEndReason.SESSION_FINISHED);

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        String suffix = reason == null || reason.isBlank() ? "" : ": " + reason;
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_FINISHED, actor, "Draft session finished" + suffix + ".");
        return snapshot;
    }

    private DraftSessionEntity loadSessionForUpdate(Long sessionId) {
        return draftSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("?쒕옒?꾪듃 ?몄뀡??李얠쓣 ???놁뒿?덈떎."));
    }

    private void assertLiveSession(DraftSessionEntity session) {
        if (!"LIVE".equals(session.getStatus())) {
            throw new IllegalArgumentException("LIVE ?곹깭???몄뀡?먯꽌留??ㅽ뻾?????덉뒿?덈떎.");
        }
    }

    private void assertCandidatePickable(DraftCandidateEntity candidate, Long sessionId, Long candidateUserId) {
        if (draftPickRepository.existsByDraftSessionIdAndCandidateUserId(sessionId, candidateUserId)) {
            throw new IllegalArgumentException("?대? ?좏깮???꾨낫?낅땲??");
        }
        if (!CANDIDATE_WAITING.equals(candidate.getStatus())) {
            throw new IllegalArgumentException("?湲??곹깭 ?꾨낫留??좏깮?????덉뒿?덈떎.");
        }
    }

    private void advanceTurnOrFinish(DraftSessionEntity session, LocalDateTime now) {
        long nextPickNo = session.getCurrentPickNo() + 1L;
        DraftOrderEntity nextOrder = draftOrderRepository.findByDraftSessionIdAndPickNo(session.getId(), nextPickNo)
                .orElse(null);
        if (nextOrder == null) {
            session.finish(now);
            return;
        }
        session.advanceTurn((int) nextPickNo, nextOrder.getDraftTeamId(), now.plusSeconds(session.getPickTimeSeconds()));
    }

    private long requireCurrentPickNo(DraftSessionEntity session) {
        if (session.getCurrentPickNo() == null || session.getCurrentPickNo() <= 0) {
            throw new IllegalArgumentException("?꾩옱 ??踰덊샇媛 ?щ컮瑜댁? ?딆뒿?덈떎.");
        }
        return session.getCurrentPickNo().longValue();
    }

    private DraftOrderEntity requireCurrentOrder(DraftSessionEntity session) {
        return requireOrder(session.getId(), requireCurrentPickNo(session));
    }

    private DraftOrderEntity requireOrder(Long sessionId, long pickNo) {
        return draftOrderRepository.findByDraftSessionIdAndPickNo(sessionId, pickNo)
                .orElseThrow(() -> new IllegalArgumentException("?꾩옱 ?쒕옒?꾪듃 ?쒖꽌瑜?李얠쓣 ???놁뒿?덈떎."));
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
