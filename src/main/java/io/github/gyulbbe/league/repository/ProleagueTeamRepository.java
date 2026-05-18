package io.github.gyulbbe.league.repository;

import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProleagueTeamRepository extends JpaRepository<ProleagueTeamEntity, Long> {
    List<ProleagueTeamEntity> findAllByLeagueIdOrderByDisplayOrderAscIdAsc(Long leagueId);

    long countByLeagueId(Long leagueId);

    @Query("""
            select count(t) > 0
            from ProleagueTeamEntity t
            where t.leagueId = :leagueId
              and (t.leaderId = :userId or t.viceLeaderId = :userId)
            """)
    boolean existsLeaderOrViceLeaderByLeagueIdAndUserId(
            @Param("leagueId") Long leagueId,
            @Param("userId") Long userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProleagueTeamEntity t set t.draftTeamId = null where t.leagueId = :leagueId")
    int unlinkDraftTeamsByLeagueId(@Param("leagueId") Long leagueId);

    @Modifying
    @Query("delete from ProleagueTeamEntity t where t.leagueId = :leagueId")
    int deleteByLeagueId(@Param("leagueId") Long leagueId);
}
