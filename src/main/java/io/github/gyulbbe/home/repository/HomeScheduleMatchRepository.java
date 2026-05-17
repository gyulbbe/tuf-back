package io.github.gyulbbe.home.repository;

import io.github.gyulbbe.home.entity.HomeScheduleMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HomeScheduleMatchRepository extends JpaRepository<HomeScheduleMatchEntity, Long> {
    List<HomeScheduleMatchEntity> findByScheduleIdInOrderByScheduleIdAscDisplayOrderAscIdAsc(Collection<Long> scheduleIds);

    boolean existsByMapId(Long mapId);
}
