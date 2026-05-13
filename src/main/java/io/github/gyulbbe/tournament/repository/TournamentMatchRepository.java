package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentMatchRepository extends JpaRepository<TournamentMatchEntity, Long> {
    List<TournamentMatchEntity> findAllByStageIdOrderByDisplayOrderAsc(Long stageId);

    List<TournamentMatchEntity> findAllByGroupIdOrderByDisplayOrderAsc(Long groupId);

    Optional<TournamentMatchEntity> findByGroupIdAndMatchKey(Long groupId, String matchKey);

    List<TournamentMatchEntity> findAllByStageIdAndStatus(Long stageId, String status);

    List<TournamentMatchEntity> findAllByGroupIdInOrderByDisplayOrderAsc(List<Long> groupIds);

    List<TournamentMatchEntity> findAllByStageIdInOrderByDisplayOrderAsc(List<Long> stageIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentMatchEntity m where m.groupId in :groupIds")
    int deleteByGroupIdIn(@Param("groupIds") List<Long> groupIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentMatchEntity m where m.stageId in :stageIds")
    int deleteByStageIdIn(@Param("stageIds") List<Long> stageIds);
}
