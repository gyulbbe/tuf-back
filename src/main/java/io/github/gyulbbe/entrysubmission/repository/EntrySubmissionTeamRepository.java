package io.github.gyulbbe.entrysubmission.repository;

import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionTeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntrySubmissionTeamRepository extends JpaRepository<EntrySubmissionTeamEntity, Long> {
    int deleteByEntrySubmissionSessionId(Long entrySubmissionSessionId);

    long countByEntrySubmissionSessionId(Long entrySubmissionSessionId);

    List<EntrySubmissionTeamEntity> findAllByEntrySubmissionSessionIdOrderByDisplayOrderAscIdAsc(Long entrySubmissionSessionId);

    Optional<EntrySubmissionTeamEntity> findByEntrySubmissionSessionIdAndDisplayOrder(Long entrySubmissionSessionId, Integer displayOrder);

    Optional<EntrySubmissionTeamEntity> findByEntrySubmissionSessionIdAndCaptainUserId(Long entrySubmissionSessionId, Long captainUserId);
}
