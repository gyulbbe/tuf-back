package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftPickEntity;
import io.github.gyulbbe.draft.entity.DraftPickId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DraftPickRepository extends JpaRepository<DraftPickEntity, DraftPickId> {
    long countByDraftSessionId(Long draftSessionId);

    List<DraftPickEntity> findAllByDraftSessionIdOrderByPickNoAsc(Long draftSessionId);

    boolean existsByDraftSessionIdAndCandidateUserId(Long draftSessionId, Long candidateUserId);

    boolean existsByDraftSessionIdAndPickNo(Long draftSessionId, Long pickNo);

    @Modifying
    @Query("delete from DraftPickEntity p where p.draftSessionId = :draftSessionId")
    int deleteByDraftSessionId(@Param("draftSessionId") Long draftSessionId);
}
