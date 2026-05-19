package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.RaceSurvivalProgressSubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RaceSurvivalProgressSubmissionRepository extends JpaRepository<RaceSurvivalProgressSubmissionEntity, Long> {

    List<RaceSurvivalProgressSubmissionEntity> findAllByTournamentIdOrderByRegDateDescIdDesc(Long tournamentId);

    List<RaceSurvivalProgressSubmissionEntity> findAllByTournamentIdAndSubmittedByUserIdOrderByRegDateDescIdDesc(
            Long tournamentId,
            Long submittedByUserId
    );

    List<RaceSurvivalProgressSubmissionEntity> findAllByTournamentIdAndStatus(Long tournamentId, String status);

    List<RaceSurvivalProgressSubmissionEntity> findAllByTournamentIdAndSubmittedByUserIdAndStatus(
            Long tournamentId,
            Long submittedByUserId,
            String status
    );

    Optional<RaceSurvivalProgressSubmissionEntity> findByIdAndTournamentId(Long id, Long tournamentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RaceSurvivalProgressSubmissionEntity s where s.tournamentId = :tournamentId")
    int deleteByTournamentId(@Param("tournamentId") Long tournamentId);
}
