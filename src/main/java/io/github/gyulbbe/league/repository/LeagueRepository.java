package io.github.gyulbbe.league.repository;

import io.github.gyulbbe.league.entity.LeagueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeagueRepository extends JpaRepository<LeagueEntity, Long> {
    Optional<LeagueEntity> findByIdAndLeagueType(Long id, String leagueType);
}
