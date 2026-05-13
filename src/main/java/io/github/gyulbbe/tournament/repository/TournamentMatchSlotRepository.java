package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentMatchSlotRepository extends JpaRepository<TournamentMatchSlotEntity, Long> {
    List<TournamentMatchSlotEntity> findAllByMatchIdOrderBySlotNoAsc(Long matchId);

    List<TournamentMatchSlotEntity> findAllBySourceMatchId(Long sourceMatchId);

    List<TournamentMatchSlotEntity> findAllByMatchIdInOrderBySlotNoAsc(List<Long> matchIds);

    Optional<TournamentMatchSlotEntity> findByMatchIdAndSlotNo(Long matchId, Integer slotNo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentMatchSlotEntity s where s.matchId in :matchIds")
    int deleteByMatchIdIn(@Param("matchIds") List<Long> matchIds);
}
