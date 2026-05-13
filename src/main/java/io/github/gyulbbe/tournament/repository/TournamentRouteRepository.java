package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentRouteRepository extends JpaRepository<TournamentRouteEntity, Long> {
    List<TournamentRouteEntity> findAllByFromMatchId(Long fromMatchId);

    Optional<TournamentRouteEntity> findByFromMatchIdAndOutcome(Long fromMatchId, String outcome);

    List<TournamentRouteEntity> findAllByToMatchId(Long toMatchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentRouteEntity r where r.fromMatchId in :matchIds")
    int deleteByFromMatchIdIn(@Param("matchIds") List<Long> matchIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentRouteEntity r where r.toMatchId in :matchIds")
    int deleteByToMatchIdIn(@Param("matchIds") List<Long> matchIds);
}
