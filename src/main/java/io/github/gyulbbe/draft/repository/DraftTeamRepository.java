package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DraftTeamRepository extends JpaRepository<DraftTeamEntity, Long> {
    boolean existsByIdAndDraftSessionId(Long id, Long draftSessionId);

    List<DraftTeamEntity> findAllByDraftSessionId(Long draftSessionId);

    void deleteAllByDraftSessionId(Long draftSessionId);
}
