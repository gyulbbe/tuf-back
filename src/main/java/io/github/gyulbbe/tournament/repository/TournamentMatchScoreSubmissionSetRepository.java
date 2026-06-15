package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentMatchScoreSubmissionSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TournamentMatchScoreSubmissionSetRepository extends JpaRepository<TournamentMatchScoreSubmissionSetEntity, Long> {

    List<TournamentMatchScoreSubmissionSetEntity> findAllByScoreSubmissionIdOrderBySetNoAsc(Long scoreSubmissionId);

    List<TournamentMatchScoreSubmissionSetEntity> findAllByScoreSubmissionIdInOrderByScoreSubmissionIdAscSetNoAsc(Collection<Long> scoreSubmissionIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentMatchScoreSubmissionSetEntity s where s.scoreSubmissionId in :scoreSubmissionIds")
    int deleteByScoreSubmissionIdIn(@Param("scoreSubmissionIds") Collection<Long> scoreSubmissionIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from TournamentMatchScoreSubmissionSetEntity s
            where s.scoreSubmissionId in (
                select sub.id
                from TournamentMatchScoreSubmissionEntity sub
                where sub.tournamentId = :tournamentId
            )
            """)
    int deleteByTournamentId(@Param("tournamentId") Long tournamentId);
}
