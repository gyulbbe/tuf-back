package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DraftTeamRepository extends JpaRepository<DraftTeamEntity, Long> {
    boolean existsByIdAndDraftSessionId(Long id, Long draftSessionId);

    long countByDraftSessionId(Long draftSessionId);

    List<DraftTeamEntity> findAllByDraftSessionId(Long draftSessionId);

    @Modifying
    @Query("delete from DraftTeamEntity t where t.draftSessionId = :draftSessionId")
    int deleteByDraftSessionId(@Param("draftSessionId") Long draftSessionId);
}
