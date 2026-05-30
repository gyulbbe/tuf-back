package io.github.gyulbbe.entrysubmission.service;

import io.github.gyulbbe.entrysubmission.auth.EntrySubmissionActor;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionSessionEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionTeamEntity;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EntrySubmissionPermissionService {

    private final EntrySubmissionTeamRepository entrySubmissionTeamRepository;

    public void assertAuthenticated(EntrySubmissionActor actor) {
        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }
    }

    public boolean isOwner(EntrySubmissionSessionEntity session, EntrySubmissionActor actor) {
        return actor != null && actor.userPk() != null && Objects.equals(session.getOwnerUserId(), actor.userPk());
    }

    public boolean isAdmin(EntrySubmissionActor actor) {
        return actor != null
                && ("ROLE_MASTER".equals(actor.role())
                || "ROLE_MANAGER".equals(actor.role())
                || "ROLE_ADMIN".equals(actor.role()));
    }

    public void assertOwnerOrAdmin(EntrySubmissionSessionEntity session, EntrySubmissionActor actor) {
        assertAuthenticated(actor);
        if (!isOwner(session, actor) && !isAdmin(actor)) {
            throw new SecurityException("Only the session owner or an admin can perform this action.");
        }
    }

    public Optional<EntrySubmissionTeamEntity> findCaptainTeam(Long sessionId, Long userPk) {
        if (sessionId == null || userPk == null) {
            return Optional.empty();
        }
        return entrySubmissionTeamRepository.findByEntrySubmissionSessionIdAndCaptainUserId(sessionId, userPk);
    }
}
