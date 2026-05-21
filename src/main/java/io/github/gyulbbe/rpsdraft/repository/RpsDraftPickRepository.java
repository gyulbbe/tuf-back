package io.github.gyulbbe.rpsdraft.repository;

import io.github.gyulbbe.rpsdraft.entity.RpsDraftPickEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftPickId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RpsDraftPickRepository extends JpaRepository<RpsDraftPickEntity, RpsDraftPickId> {
    int deleteByRpsDraftSessionId(Long rpsDraftSessionId);

    long countByRpsDraftSessionId(Long rpsDraftSessionId);

    boolean existsByRpsDraftSessionIdAndCandidateId(Long rpsDraftSessionId, Long candidateId);

    List<RpsDraftPickEntity> findAllByRpsDraftSessionIdOrderByPickNoAsc(Long rpsDraftSessionId);
}
