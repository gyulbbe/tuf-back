package io.github.gyulbbe.rpsdraft.repository;

import io.github.gyulbbe.rpsdraft.entity.RpsDraftTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RpsDraftTeamRepository extends JpaRepository<RpsDraftTeamEntity, Long> {
    int deleteByRpsDraftSessionId(Long rpsDraftSessionId);

    boolean existsByIdAndRpsDraftSessionId(Long id, Long rpsDraftSessionId);

    long countByRpsDraftSessionId(Long rpsDraftSessionId);

    List<RpsDraftTeamEntity> findAllByRpsDraftSessionIdOrderByDisplayOrderAscIdAsc(Long rpsDraftSessionId);

    Optional<RpsDraftTeamEntity> findByRpsDraftSessionIdAndDisplayOrder(Long rpsDraftSessionId, Integer displayOrder);

    Optional<RpsDraftTeamEntity> findByRpsDraftSessionIdAndPickerUserId(Long rpsDraftSessionId, Long pickerUserId);

    boolean existsByRpsDraftSessionIdAndPickerUserId(Long rpsDraftSessionId, Long pickerUserId);
}
