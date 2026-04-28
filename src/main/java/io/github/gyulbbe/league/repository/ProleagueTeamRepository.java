package io.github.gyulbbe.league.repository;

import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProleagueTeamRepository extends JpaRepository<ProleagueTeamEntity, Long> {
}
