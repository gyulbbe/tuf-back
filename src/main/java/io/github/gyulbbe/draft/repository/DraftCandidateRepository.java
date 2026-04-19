package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftCandidateEntity;
import io.github.gyulbbe.draft.entity.DraftCandidateId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DraftCandidateRepository extends JpaRepository<DraftCandidateEntity, DraftCandidateId> {
    List<DraftCandidateEntity> findAllByDraftSessionId(Long draftSessionId);

    boolean existsByDraftSessionIdAndCandidateUserId(Long draftSessionId, Long candidateUserId);

    void deleteAllByDraftSessionId(Long draftSessionId);
}
