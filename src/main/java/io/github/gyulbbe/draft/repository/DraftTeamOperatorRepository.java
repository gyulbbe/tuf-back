package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftTeamOperatorEntity;
import io.github.gyulbbe.draft.entity.DraftTeamOperatorId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DraftTeamOperatorRepository extends JpaRepository<DraftTeamOperatorEntity, DraftTeamOperatorId> {
    List<DraftTeamOperatorEntity> findAllByDraftTeamId(Long draftTeamId);

    List<DraftTeamOperatorEntity> findAllByDraftTeamIdIn(Collection<Long> draftTeamIds);

    void deleteAllByDraftTeamId(Long draftTeamId);

    void deleteAllByDraftTeamIdIn(Collection<Long> draftTeamIds);
}
