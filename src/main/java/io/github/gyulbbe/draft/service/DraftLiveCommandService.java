package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftLiveEventType;
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

@Service
@Transactional
@RequiredArgsConstructor
public class DraftLiveCommandService {

    private final DraftSessionRepository draftSessionRepository;
    private final DraftOrderRepository draftOrderRepository;
    private final DraftCandidateRepository draftCandidateRepository;
    private final DraftPickRepository draftPickRepository;
    private final DraftPermissionService draftPermissionService;
    private final DraftSnapshotService draftSnapshotService;
    private final DraftEventPublisher draftEventPublisher;
    private final DraftLiveSessionTracker draftLiveSessionTracker;

    public DraftLiveSnapshotResponseDto startSession(Long sessionId, AuthActor actor) {
        draftPermissionService.assertAdmin(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        if (!"READY".equals(session.getStatus()) && !"PAUSED".equals(session.getStatus())) {
            throw new IllegalArgumentException("READY 또는 PAUSED 상태의 세션만 시작할 수 있습니다.");
        }

        DraftOrderEntity firstOrder = draftOrderRepository.findByDraftSessionIdAndPickNo(sessionId, 1L)
                .orElseThrow(() -> new IllegalArgumentException("첫 번째 드래프트 순번이 없습니다."));

        LocalDateTime now = LocalDateTime.now();
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

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_PAUSED, actor, "Draft session paused.");
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto resumeSession(Long sessionId, AuthActor actor, Integer seconds) {
        draftPermissionService.assertAdmin(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        if (!"PAUSED".equals(session.getStatus())) {
            throw new IllegalArgumentException("PAUSED 상태의 세션만 재개할 수 있습니다.");
        }

        int resumeSeconds = seconds != null ? seconds : session.getPickTimeSeconds();
        if (resumeSeconds <= 0) {
            throw new IllegalArgumentException("재개 시간은 1초 이상이어야 합니다.");
        }

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
            throw new IllegalArgumentException("연장 시간은 1초 이상이어야 합니다.");
        }
        if (session.getDeadlineAt() == null) {
            throw new IllegalArgumentException("현재 마감 시간이 없습니다.");
        }

        session.extendDeadlineAt(session.getDeadlineAt().plusSeconds(seconds));

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        publishAfterCommit(sessionId, DraftLiveEventType.TIMER_EXTENDED, actor, "Turn timer extended.");
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto pick(Long sessionId, Long candidateUserId, AuthActor actor) {
        if (candidateUserId == null) {
            throw new IllegalArgumentException("후보 유저 ID는 필수입니다.");
        }
        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        assertLiveSession(session);

        Long currentDraftTeamId = session.getCurrentDraftTeamId();
        if (currentDraftTeamId == null) {
            throw new IllegalArgumentException("현재 턴 팀이 없습니다.");
        }
        if (!draftPermissionService.canPickForTeam(currentDraftTeamId, actor.userPk())) {
            throw new IllegalArgumentException("현재 턴의 지정된 픽 권한자만 지명할 수 있습니다.");
        }

        DraftOrderEntity currentOrder = draftOrderRepository
                .findByDraftSessionIdAndPickNo(sessionId, session.getCurrentPickNo().longValue())
                .orElseThrow(() -> new IllegalArgumentException("현재 드래프트 순번을 찾을 수 없습니다."));
        if (!currentDraftTeamId.equals(currentOrder.getDraftTeamId())) {
            throw new IllegalArgumentException("현재 세션 팀 정보와 드래프트 순번이 일치하지 않습니다.");
        }

        DraftCandidateEntity candidate = draftCandidateRepository.findById(new DraftCandidateId(sessionId, candidateUserId))
                .orElseThrow(() -> new IllegalArgumentException("후보를 찾을 수 없습니다."));
        assertCandidatePickable(candidate, sessionId, candidateUserId);

        LocalDateTime now = LocalDateTime.now();
        DraftPickEntity pick = DraftPickEntity.builder()
                .draftSessionId(sessionId)
                .roundNo(currentOrder.getRoundNo())
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
            publishAfterCommit(sessionId, DraftLiveEventType.SESSION_FINISHED, actor, "Final pick completed. Draft session finished.");
        } else {
            publishAfterCommit(sessionId, DraftLiveEventType.PICK_COMPLETED, actor, "Pick completed.");
        }
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto forceSkip(Long sessionId, AuthActor actor, String reason) {
        draftPermissionService.assertAdminOrSystem(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        assertLiveSession(session);

        draftOrderRepository.findByDraftSessionIdAndPickNo(sessionId, session.getCurrentPickNo().longValue())
                .orElseThrow(() -> new IllegalArgumentException("현재 드래프트 순번을 찾을 수 없습니다."));

        advanceTurnOrFinish(session, LocalDateTime.now());

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        if ("FINISHED".equals(snapshot.getSession().getStatus())) {
            draftLiveSessionTracker.refreshAfterCommit();
            publishAfterCommit(sessionId, DraftLiveEventType.SESSION_FINISHED, actor, "Draft session finished.");
        } else {
            String suffix = reason == null || reason.isBlank() ? "" : ": " + reason;
            publishAfterCommit(sessionId, DraftLiveEventType.PICK_SKIPPED, actor, "Current pick skipped" + suffix + ".");
        }
        return snapshot;
    }

    public DraftLiveSnapshotResponseDto finishSession(Long sessionId, AuthActor actor, String reason) {
        draftPermissionService.assertAdmin(actor);

        DraftSessionEntity session = loadSessionForUpdate(sessionId);
        if ("FINISHED".equals(session.getStatus())) {
            throw new IllegalArgumentException("이미 종료된 세션입니다.");
        }

        session.finish(LocalDateTime.now());
        draftLiveSessionTracker.refreshAfterCommit();

        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getSnapshot(sessionId, actor);
        String suffix = reason == null || reason.isBlank() ? "" : ": " + reason;
        publishAfterCommit(sessionId, DraftLiveEventType.SESSION_FINISHED, actor, "Draft session finished" + suffix + ".");
        return snapshot;
    }

    private DraftSessionEntity loadSessionForUpdate(Long sessionId) {
        return draftSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("드래프트 세션을 찾을 수 없습니다."));
    }

    private void assertLiveSession(DraftSessionEntity session) {
        if (!"LIVE".equals(session.getStatus())) {
            throw new IllegalArgumentException("LIVE 상태의 세션에서만 수행할 수 있습니다.");
        }
    }

    private void assertCandidatePickable(DraftCandidateEntity candidate, Long sessionId, Long candidateUserId) {
        if (draftPickRepository.existsByDraftSessionIdAndCandidateUserId(sessionId, candidateUserId)) {
            throw new IllegalArgumentException("이미 선택된 후보입니다.");
        }
        if (!"WAITING".equals(candidate.getStatus())) {
            throw new IllegalArgumentException("대기 상태 후보만 선택할 수 있습니다.");
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

    private void publishAfterCommit(Long sessionId, DraftLiveEventType type, AuthActor actor, String message) {
        draftEventPublisher.publishAfterCommit(sessionId, type, actor, message);
    }
}
