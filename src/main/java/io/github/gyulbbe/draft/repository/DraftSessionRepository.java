package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DraftSessionRepository extends JpaRepository<DraftSessionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DraftSessionEntity s where s.id = :sessionId")
    Optional<DraftSessionEntity> findByIdForUpdate(Long sessionId);

    boolean existsByStatus(String status);

    List<DraftSessionEntity> findAllByStatusAndDeadlineAtLessThanEqual(String status, LocalDateTime deadlineAt);
}
