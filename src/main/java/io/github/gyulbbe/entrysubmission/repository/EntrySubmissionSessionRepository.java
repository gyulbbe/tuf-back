package io.github.gyulbbe.entrysubmission.repository;

import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EntrySubmissionSessionRepository extends JpaRepository<EntrySubmissionSessionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EntrySubmissionSessionEntity s where s.id = :sessionId")
    Optional<EntrySubmissionSessionEntity> findByIdForUpdate(Long sessionId);

    List<EntrySubmissionSessionEntity> findAllByOrderByRegDateDescIdDesc();

    long countBySourceRpsDraftSessionId(Long sourceRpsDraftSessionId);
}
