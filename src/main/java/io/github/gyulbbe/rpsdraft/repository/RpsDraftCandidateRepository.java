package io.github.gyulbbe.rpsdraft.repository;

import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RpsDraftCandidateRepository extends JpaRepository<RpsDraftCandidateEntity, Long> {
    int deleteByRpsDraftSessionId(Long rpsDraftSessionId);

    long countByRpsDraftSessionId(Long rpsDraftSessionId);

    long countByRpsDraftSessionIdAndStatus(Long rpsDraftSessionId, String status);

    List<RpsDraftCandidateEntity> findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(Long rpsDraftSessionId);
}
