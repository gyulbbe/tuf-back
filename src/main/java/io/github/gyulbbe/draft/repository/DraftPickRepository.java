package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftPickEntity;
import io.github.gyulbbe.draft.entity.DraftPickId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DraftPickRepository extends JpaRepository<DraftPickEntity, DraftPickId> {
    List<DraftPickEntity> findAllByDraftSessionIdOrderByPickNoAsc(Long draftSessionId);

    boolean existsByDraftSessionIdAndCandidateUserId(Long draftSessionId, Long candidateUserId);

    boolean existsByDraftSessionIdAndPickNo(Long draftSessionId, Long pickNo);

    void deleteAllByDraftSessionId(Long draftSessionId);
}
