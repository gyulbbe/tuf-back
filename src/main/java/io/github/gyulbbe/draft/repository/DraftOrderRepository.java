package io.github.gyulbbe.draft.repository;

import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftOrderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DraftOrderRepository extends JpaRepository<DraftOrderEntity, DraftOrderId> {
    long countByDraftSessionId(Long draftSessionId);

    List<DraftOrderEntity> findAllByDraftSessionIdOrderByPickNoAsc(Long draftSessionId);

    java.util.Optional<DraftOrderEntity> findByDraftSessionIdAndPickNo(Long draftSessionId, Long pickNo);

    @Modifying
    @Query("delete from DraftOrderEntity o where o.draftSessionId = :draftSessionId")
    int deleteByDraftSessionId(@Param("draftSessionId") Long draftSessionId);
}
