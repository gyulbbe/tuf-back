package io.github.gyulbbe.tournament.repository;

import io.github.gyulbbe.tournament.entity.RaceSurvivalProgressSubmissionMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RaceSurvivalProgressSubmissionMatchRepository extends JpaRepository<RaceSurvivalProgressSubmissionMatchEntity, Long> {

    List<RaceSurvivalProgressSubmissionMatchEntity> findAllBySubmissionIdOrderByMatchOrderAsc(Long submissionId);

    List<RaceSurvivalProgressSubmissionMatchEntity> findAllBySubmissionIdInOrderBySubmissionIdAscMatchOrderAsc(List<Long> submissionIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RaceSurvivalProgressSubmissionMatchEntity m where m.submissionId in :submissionIds")
    int deleteBySubmissionIdIn(@Param("submissionIds") List<Long> submissionIds);
}
