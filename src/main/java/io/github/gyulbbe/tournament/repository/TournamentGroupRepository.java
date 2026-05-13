package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentGroupRepository extends JpaRepository<TournamentGroupEntity, Long> {
    List<TournamentGroupEntity> findAllByStageIdOrderByDisplayOrderAsc(Long stageId);

    Optional<TournamentGroupEntity> findByStageIdAndGroupCode(Long stageId, String groupCode);

    List<TournamentGroupEntity> findAllByStageIdInOrderByDisplayOrderAsc(List<Long> stageIds);

    long countByStageIdIn(List<Long> stageIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentGroupEntity g where g.stageId in :stageIds")
    int deleteByStageIdIn(@Param("stageIds") List<Long> stageIds);
}
