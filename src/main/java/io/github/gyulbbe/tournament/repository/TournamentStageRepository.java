package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentStageRepository extends JpaRepository<TournamentStageEntity, Long> {
    List<TournamentStageEntity> findAllByTournamentIdOrderByDisplayOrderAsc(Long tournamentId);

    Optional<TournamentStageEntity> findByTournamentIdAndStageNo(Long tournamentId, Integer stageNo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentStageEntity s where s.tournamentId = :tournamentId")
    int deleteByTournamentId(@Param("tournamentId") Long tournamentId);
}
