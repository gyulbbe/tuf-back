package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TournamentGroupEntryRepository extends JpaRepository<TournamentGroupEntryEntity, Long> {
    List<TournamentGroupEntryEntity> findAllByGroupIdOrderByGroupSeedNoAsc(Long groupId);

    boolean existsByGroupIdAndParticipantId(Long groupId, Long participantId);

    List<TournamentGroupEntryEntity> findAllByGroupIdInOrderByGroupSeedNoAsc(List<Long> groupIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TournamentGroupEntryEntity e where e.groupId in :groupIds")
    int deleteByGroupIdIn(@Param("groupIds") List<Long> groupIds);
}
