package io.github.gyulbbe.home.repository;

import io.github.gyulbbe.home.entity.HomeScheduleMatchPlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HomeScheduleMatchPlayerRepository extends JpaRepository<HomeScheduleMatchPlayerEntity, Long> {
    List<HomeScheduleMatchPlayerEntity> findByMatchIdInOrderByMatchIdAscSideAscSlotOrderAscIdAsc(Collection<Long> matchIds);
}
