package io.github.gyulbbe.common.utils.trace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TraceRepository extends JpaRepository<TraceEntity, Long> {
}
