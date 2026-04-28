package io.github.gyulbbe.match.repository;

import io.github.gyulbbe.match.entity.MatchInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchInfoRepository extends JpaRepository<MatchInfoEntity, Long> {
}
