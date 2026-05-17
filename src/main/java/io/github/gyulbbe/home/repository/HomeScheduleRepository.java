package io.github.gyulbbe.home.repository;

import io.github.gyulbbe.home.entity.HomeScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HomeScheduleRepository extends JpaRepository<HomeScheduleEntity, Long>, JpaSpecificationExecutor<HomeScheduleEntity> {
    @Query(value = """
            SELECT id,
                   schedule_group,
                   title,
                   description,
                   scheduled_at,
                   target_url,
                   link_type,
                   display_priority,
                   reg_date,
                   update_date
            FROM (
                SELECT hs.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY hs.schedule_group
                           ORDER BY hs.display_priority DESC, hs.scheduled_at ASC, hs.id ASC
                       ) AS row_no
                FROM home_schedules hs
                WHERE hs.scheduled_at >= :now
            )
            WHERE row_no = 1
            ORDER BY scheduled_at ASC, display_priority DESC, id ASC
            """, nativeQuery = true)
    List<HomeScheduleEntity> findPublicRepresentativeSchedules(@Param("now") LocalDateTime now);
}
