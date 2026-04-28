package io.github.gyulbbe.rpsdraft.repository;

import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RpsDraftSessionRepository extends JpaRepository<RpsDraftSessionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RpsDraftSessionEntity s where s.id = :sessionId")
    Optional<RpsDraftSessionEntity> findByIdForUpdate(Long sessionId);
}
