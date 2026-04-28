package io.github.gyulbbe.league.repository;

import io.github.gyulbbe.league.entity.LeagueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeagueRepository extends JpaRepository<LeagueEntity, Long> {
}
