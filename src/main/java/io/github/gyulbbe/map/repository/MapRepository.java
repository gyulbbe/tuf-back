package io.github.gyulbbe.map.repository;

import io.github.gyulbbe.map.entity.MapEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MapRepository extends JpaRepository<MapEntity, Long> {
    boolean existsByMapName(String mapName);

    boolean existsByMapNameAndIdNot(String mapName, Long id);

    @Query("""
            select m
            from MapEntity m
            where (:keyword is null or lower(m.mapName) like concat('%', :keyword, '%'))
            """)
    Page<MapEntity> findAdminMaps(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select m
            from MapEntity m
            where m.mapName is not null
              and (:keyword = '' or lower(m.mapName) like concat('%', :keyword, '%'))
            order by
              case when lower(m.mapName) = :keyword then 0 else 1 end,
              lower(m.mapName) asc,
              m.id asc
            """)
    List<MapEntity> searchByMapNameForAdmin(@Param("keyword") String keyword, Pageable pageable);
}
