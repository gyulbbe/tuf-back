package io.github.gyulbbe.rpsdraft.repository;

import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RpsDraftCandidateRepository extends JpaRepository<RpsDraftCandidateEntity, RpsDraftCandidateId> {
    int deleteByRpsDraftSessionId(Long rpsDraftSessionId);

    long countByRpsDraftSessionId(Long rpsDraftSessionId);

    long countByRpsDraftSessionIdAndStatus(Long rpsDraftSessionId, String status);

    boolean existsByRpsDraftSessionIdAndCandidateUserId(Long rpsDraftSessionId, Long candidateUserId);

    List<RpsDraftCandidateEntity> findAllByRpsDraftSessionId(Long rpsDraftSessionId);
}
