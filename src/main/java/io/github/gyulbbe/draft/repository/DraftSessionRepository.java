package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftSessionRepository extends JpaRepository<DraftSessionEntity, Long> {
}
