package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftOrderId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DraftOrderRepository extends JpaRepository<DraftOrderEntity, DraftOrderId> {
    List<DraftOrderEntity> findAllByDraftSessionIdOrderByPickNoAsc(Long draftSessionId);

    java.util.Optional<DraftOrderEntity> findByDraftSessionIdAndPickNo(Long draftSessionId, Long pickNo);

    void deleteAllByDraftSessionId(Long draftSessionId);
}
