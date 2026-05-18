package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentResultSlotRepository extends JpaRepository<TournamentResultSlotEntity, Long> {
    List<TournamentResultSlotEntity> findAllByStageIdOrderByRankNoAscIdAsc(Long stageId);

    List<TournamentResultSlotEntity> findAllByGroupIdOrderByRankNoAscIdAsc(Long groupId);

    Optional<TournamentResultSlotEntity> findByStageIdAndResultKey(Long stageId, String resultKey);

    List<TournamentResultSlotEntity> findAllByGroupIdInOrderByRankNoAscIdAsc(List<Long> groupIds);

    @Query("""
            select count(r)
            from TournamentResultSlotEntity r
            where r.stageId in :stageIds
              and r.participantId is not null
            """)
    long countDecidedByStageIdIn(@Param("stageIds") List<Long> stageIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentResultSlotEntity r where r.groupId in :groupIds")
    int deleteByGroupIdIn(@Param("groupIds") List<Long> groupIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentResultSlotEntity r where r.stageId in :stageIds")
    int deleteByStageIdIn(@Param("stageIds") List<Long> stageIds);
}
