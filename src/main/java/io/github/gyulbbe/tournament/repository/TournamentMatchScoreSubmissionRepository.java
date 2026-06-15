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

    boolean existsByTournamentIdAndMatchIdAndStatus(Long tournamentId, Long matchId, String status);

    @Query("""
            select case when count(s) > 0 then true else false end
            from TournamentMatchScoreSubmissionEntity s
            where s.tournamentId = :tournamentId
              and s.matchId = :matchId
              and s.status <> :excludedStatus
            """)
    boolean existsByTournamentIdAndMatchIdAndStatusNot(
            @Param("tournamentId") Long tournamentId,
            @Param("matchId") Long matchId,
            @Param("excludedStatus") String excludedStatus
    );

    List<TournamentMatchScoreSubmissionEntity> findAllByTournamentIdAndMatchIdAndSubmittedByUserIdOrderByRegDateDescIdDesc(
            Long tournamentId,
            Long matchId,
            Long submittedByUserId
    );

    Optional<TournamentMatchScoreSubmissionEntity> findByIdAndTournamentIdAndMatchId(Long id, Long tournamentId, Long matchId);

    long countByTournamentId(Long tournamentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentMatchScoreSubmissionEntity s where s.tournamentId = :tournamentId")
    int deleteByTournamentId(@Param("tournamentId") Long tournamentId);
}
