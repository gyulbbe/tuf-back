package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentClanShareSendLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TournamentClanShareSendLogRepository extends JpaRepository<TournamentClanShareSendLogEntity, Long> {
    long countByTournamentId(Long tournamentId);

    Optional<TournamentClanShareSendLogEntity> findFirstByTournamentIdOrderByRegDateDescIdDesc(Long tournamentId);

    List<TournamentClanShareSendLogEntity> findAllByTournamentIdAndMatchIdInOrderByRegDateDescIdDesc(
            Long tournamentId,
            Collection<Long> matchIds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentClanShareSendLogEntity l where l.tournamentId = :tournamentId")
    int deleteByTournamentId(@Param("tournamentId") Long tournamentId);
}
