package io.github.gyulbbe.league.repository;

import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeagueParticipationRepository extends JpaRepository<LeagueParticipationEntity, Long> {
    List<LeagueParticipationEntity> findAllByLeagueIdOrderByIdAsc(Long leagueId);

    long countByLeagueId(Long leagueId);

    long countByLeagueIdAndStatus(Long leagueId, String status);

    @Modifying
    @Query("delete from LeagueParticipationEntity p where p.leagueId = :leagueId")
    int deleteByLeagueId(@Param("leagueId") Long leagueId);
}
