package io.github.gyulbbe.league.repository;

import io.github.gyulbbe.league.entity.ProleagueTeamMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProleagueTeamMemberRepository extends JpaRepository<ProleagueTeamMemberEntity, Long> {

    List<ProleagueTeamMemberEntity> findAllByLeagueIdAndStatusOrderByDisplayOrderAscIdAsc(Long leagueId, String status);

    boolean existsByLeagueIdAndUserIdAndStatus(Long leagueId, Long userId, String status);

    @Query("""
            select count(m) > 0
            from ProleagueTeamMemberEntity m
            where m.leagueId = :leagueId
              and m.userId = :userId
              and m.status = :status
              and (m.sourceDraftSessionId is null or m.sourceDraftSessionId <> :sourceDraftSessionId)
            """)
    boolean existsActiveConflict(
            @Param("leagueId") Long leagueId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("sourceDraftSessionId") Long sourceDraftSessionId
    );

    @Modifying
    @Query("""
            delete from ProleagueTeamMemberEntity m
            where m.leagueId = :leagueId
              and m.source = :source
              and m.sourceDraftSessionId = :sourceDraftSessionId
            """)
    int deleteByLeagueIdAndSourceAndSourceDraftSessionId(
            @Param("leagueId") Long leagueId,
            @Param("source") String source,
            @Param("sourceDraftSessionId") Long sourceDraftSessionId
    );

    @Modifying
    @Query("delete from ProleagueTeamMemberEntity m where m.leagueId = :leagueId")
    int deleteByLeagueId(@Param("leagueId") Long leagueId);
}
