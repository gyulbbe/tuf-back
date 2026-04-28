package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DraftPermissionService {

    private final DraftTeamRepository draftTeamRepository;

    public void assertAuthenticated(AuthActor actor) {
        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }
    }

    public boolean isAdmin(AuthActor actor) {
        return actor != null && (
                "ROLE_MASTER".equals(actor.role())
                        || "ROLE_MANAGER".equals(actor.role())
                        || "ROLE_ADMIN".equals(actor.role())
        );
    }

    public boolean isSystem(AuthActor actor) {
        return actor != null && "ROLE_SYSTEM".equals(actor.role());
    }

    public boolean isOwner(DraftSessionEntity session, AuthActor actor) {
        return session != null
                && actor != null
                && actor.userPk() != null
                && Objects.equals(session.getOwnerUserId(), actor.userPk());
    }

    public boolean isOwnerOrAdmin(DraftSessionEntity session, AuthActor actor) {
        return isAdmin(actor) || isOwner(session, actor);
    }

    public void assertAdmin(AuthActor actor) {
        if (!isAdmin(actor)) {
            throw new IllegalArgumentException("Only an administrator can perform this action.");
        }
    }

    public void assertAdminOrSystem(AuthActor actor) {
        if (!isAdmin(actor) && !isSystem(actor)) {
            throw new IllegalArgumentException("Only an administrator or the system can perform this action.");
        }
    }

    public void assertOwnerOrAdmin(DraftSessionEntity session, AuthActor actor) {
        assertAuthenticated(actor);
        if (!isOwnerOrAdmin(session, actor)) {
            throw new IllegalArgumentException("Only the session owner or an administrator can perform this action.");
        }
    }

    public void assertOwnerOrAdminOrSystem(DraftSessionEntity session, AuthActor actor) {
        if (isSystem(actor) || isOwnerOrAdmin(session, actor)) {
            return;
        }

        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        throw new IllegalArgumentException("Only the session owner, an administrator, or the system can perform this action.");
    }

    public boolean isCurrentPicker(Long draftTeamId, AuthActor actor) {
        return actor != null && canPickForTeam(draftTeamId, actor.userPk());
    }

    public void assertSystemOrCurrentPicker(Long draftTeamId, AuthActor actor) {
        if (isSystem(actor) || isCurrentPicker(draftTeamId, actor)) {
            return;
        }

        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        throw new IllegalArgumentException("Only the current picker or the system can skip this turn.");
    }

    public boolean canPickForTeam(Long draftTeamId, Long userPk) {
        if (draftTeamId == null || userPk == null) {
            return false;
        }
        return draftTeamRepository.findById(draftTeamId)
                .map(team -> Objects.equals(team.getPickerUserId(), userPk))
                .orElse(false);
    }
}
