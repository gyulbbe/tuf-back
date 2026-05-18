package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DraftTeamRepository extends JpaRepository<DraftTeamEntity, Long> {
    boolean existsByIdAndDraftSessionId(Long id, Long draftSessionId);

    long countByDraftSessionId(Long draftSessionId);

    List<DraftTeamEntity> findAllByDraftSessionId(Long draftSessionId);

    List<DraftTeamEntity> findAllByDraftSessionIdOrderByDisplayOrderAsc(Long draftSessionId);

    Optional<DraftTeamEntity> findByDraftSessionIdAndDisplayOrder(Long draftSessionId, Integer displayOrder);

    @Modifying
    @Query("delete from DraftTeamEntity t where t.draftSessionId = :draftSessionId")
    int deleteByDraftSessionId(@Param("draftSessionId") Long draftSessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DraftTeamEntity t
            set t.proleagueTeamId = null
            where t.proleagueTeamId in (
                select pt.id
                from ProleagueTeamEntity pt
                where pt.leagueId = :leagueId
            )
            """)
    int unlinkProleagueTeamsByLeagueId(@Param("leagueId") Long leagueId);
}
