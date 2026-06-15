package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentMatchSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TournamentMatchSetRepository extends JpaRepository<TournamentMatchSetEntity, Long> {

    List<TournamentMatchSetEntity> findAllByMatchIdOrderBySetNoAsc(Long matchId);

    List<TournamentMatchSetEntity> findAllByMatchIdInOrderByMatchIdAscSetNoAsc(Collection<Long> matchIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from TournamentMatchSetEntity s where s.matchId = :matchId")
    int deleteByMatchId(@Param("matchId") Long matchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentMatchSetEntity s where s.matchId in :matchIds")
    int deleteByMatchIdIn(@Param("matchIds") Collection<Long> matchIds);
}
