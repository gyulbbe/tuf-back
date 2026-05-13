package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentMatchScoreSubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentMatchScoreSubmissionRepository extends JpaRepository<TournamentMatchScoreSubmissionEntity, Long> {
    List<TournamentMatchScoreSubmissionEntity> findAllByTournamentIdAndMatchIdOrderByRegDateDescIdDesc(Long tournamentId, Long matchId);

    List<TournamentMatchScoreSubmissionEntity> findAllByTournamentIdAndMatchIdAndStatus(Long tournamentId, Long matchId, String status);

    List<TournamentMatchScoreSubmissionEntity> findAllByTournamentIdAndMatchIdAndSubmittedByUserIdOrderByRegDateDescIdDesc(
            Long tournamentId,
            Long matchId,
            Long submittedByUserId
    );

    Optional<TournamentMatchScoreSubmissionEntity> findByIdAndTournamentIdAndMatchId(Long id, Long tournamentId, Long matchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentMatchScoreSubmissionEntity s where s.tournamentId = :tournamentId")
    int deleteByTournamentId(@Param("tournamentId") Long tournamentId);
}
