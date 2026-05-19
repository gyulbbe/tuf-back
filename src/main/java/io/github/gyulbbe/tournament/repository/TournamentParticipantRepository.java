package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipantEntity, Long> {
    List<TournamentParticipantEntity> findAllByTournamentIdOrderBySeedNoAscIdAsc(Long tournamentId);

    Optional<TournamentParticipantEntity> findFirstByTournamentIdAndUserIdOrderBySeedNoAscIdAsc(Long tournamentId, Long userId);

    boolean existsByTournamentIdAndSeedNo(Long tournamentId, Integer seedNo);

    long countByTournamentId(Long tournamentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentParticipantEntity p where p.tournamentId = :tournamentId")
    int deleteByTournamentId(@Param("tournamentId") Long tournamentId);
}
