package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DraftSessionRepository extends JpaRepository<DraftSessionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DraftSessionEntity s where s.id = :sessionId")
    Optional<DraftSessionEntity> findByIdForUpdate(Long sessionId);

    boolean existsByStatus(String status);

    List<DraftSessionEntity> findAllByProleagueId(Long proleagueId);

    long countByProleagueId(Long proleagueId);

    List<DraftSessionEntity> findAllByStatusAndDeadlineAtLessThanEqual(String status, LocalDateTime deadlineAt);

    @Query("""
            select s
            from DraftSessionEntity s
            where s.status in :statuses
            order by coalesce(s.updateDate, s.regDate) desc,
                     s.regDate desc,
                     s.id desc
            """)
    List<DraftSessionEntity> findHomeMainOngoingSessions(
            @Param("statuses") List<String> statuses,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DraftSessionEntity s set s.proleagueId = null where s.proleagueId = :proleagueId")
    int unlinkProleagueByProleagueId(@Param("proleagueId") Long proleagueId);
}
