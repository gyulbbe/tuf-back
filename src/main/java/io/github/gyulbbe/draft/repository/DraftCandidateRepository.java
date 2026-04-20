package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftCandidateEntity;
import io.github.gyulbbe.draft.entity.DraftCandidateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DraftCandidateRepository extends JpaRepository<DraftCandidateEntity, DraftCandidateId> {
    long countByDraftSessionId(Long draftSessionId);

    List<DraftCandidateEntity> findAllByDraftSessionId(Long draftSessionId);

    boolean existsByDraftSessionIdAndCandidateUserId(Long draftSessionId, Long candidateUserId);

    @Modifying
    @Query("delete from DraftCandidateEntity c where c.draftSessionId = :draftSessionId")
    int deleteByDraftSessionId(@Param("draftSessionId") Long draftSessionId);
}
