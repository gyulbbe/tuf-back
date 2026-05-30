package io.github.gyulbbe.entrysubmission.repository;

import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionEntryEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionEntryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntrySubmissionEntryRepository extends JpaRepository<EntrySubmissionEntryEntity, EntrySubmissionEntryId> {
    int deleteByEntrySubmissionSessionId(Long entrySubmissionSessionId);

    int deleteByEntrySubmissionSessionIdAndEntrySubmissionTeamId(Long entrySubmissionSessionId, Long entrySubmissionTeamId);

    long countByEntrySubmissionSessionId(Long entrySubmissionSessionId);

    List<EntrySubmissionEntryEntity> findAllByEntrySubmissionSessionIdOrderBySetNoAscEntrySubmissionTeamIdAsc(
            Long entrySubmissionSessionId
    );
}
