package io.github.gyulbbe.league.repository;

import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeagueParticipationRepository extends JpaRepository<LeagueParticipationEntity, Long> {
}
