package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.entity.DraftTeamOperatorEntity;
import io.github.gyulbbe.draft.repository.DraftTeamOperatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DraftPermissionService {

    private final DraftTeamOperatorRepository draftTeamOperatorRepository;

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

    public void assertAdmin(AuthActor actor) {
        if (!isAdmin(actor)) {
            throw new IllegalArgumentException("관리자만 처리할 수 있습니다.");
        }
    }

    public void assertAdminOrSystem(AuthActor actor) {
        if (!isAdmin(actor) && !isSystem(actor)) {
            throw new IllegalArgumentException("관리자만 처리할 수 있습니다.");
        }
    }

    public boolean canPickForTeam(Long draftTeamId, Long userPk) {
        return draftTeamOperatorRepository
                .findByDraftTeamIdAndOperatorUserIdAndIsActiveAndCanPick(draftTeamId, userPk, "Y", "Y")
                .isPresent();
    }

    public DraftTeamOperatorEntity getCurrentPicker(Long draftTeamId) {
        return draftTeamOperatorRepository
                .findByDraftTeamIdAndCanPickAndIsActive(draftTeamId, "Y", "Y")
                .orElse(null);
    }
}
