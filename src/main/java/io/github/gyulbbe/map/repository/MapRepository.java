package io.github.gyulbbe.map.repository;

import io.github.gyulbbe.map.entity.MapEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MapRepository extends JpaRepository<MapEntity, Long> {

}
