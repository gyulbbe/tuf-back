package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftTeamEntity;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RpsDraftPermissionService {

    private final RpsDraftTeamRepository rpsDraftTeamRepository;

    public void assertAuthenticated(RpsDraftActor actor) {
        if (actor == null || actor.userPk() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }
    }

    public boolean isOwner(RpsDraftSessionEntity session, RpsDraftActor actor) {
        return actor != null && actor.userPk() != null && Objects.equals(session.getOwnerUserId(), actor.userPk());
    }

    public boolean isAdmin(RpsDraftActor actor) {
        return actor != null
                && ("ROLE_MASTER".equals(actor.role())
                || "ROLE_MANAGER".equals(actor.role())
                || "ROLE_ADMIN".equals(actor.role()));
    }

    public void assertOwner(RpsDraftSessionEntity session, RpsDraftActor actor) {
        assertAuthenticated(actor);
        if (!isOwner(session, actor)) {
            throw new IllegalArgumentException("Only the session owner can perform this action.");
        }
    }

    public void assertOwnerOrAdmin(RpsDraftSessionEntity session, RpsDraftActor actor) {
        assertAuthenticated(actor);
        if (!isOwner(session, actor) && !isAdmin(actor)) {
            throw new SecurityException("Only the session owner or an admin can perform this action.");
        }
    }

    public Optional<RpsDraftTeamEntity> findPickerTeam(Long sessionId, Long userPk) {
        if (sessionId == null || userPk == null) {
            return Optional.empty();
        }
        return rpsDraftTeamRepository.findByRpsDraftSessionIdAndPickerUserId(sessionId, userPk);
    }

    public boolean canPickForTeam(Long teamId, Long userPk) {
        if (teamId == null || userPk == null) {
            return false;
        }
        return rpsDraftTeamRepository.findById(teamId)
                .map(team -> Objects.equals(team.getPickerUserId(), userPk))
                .orElse(false);
    }
}
