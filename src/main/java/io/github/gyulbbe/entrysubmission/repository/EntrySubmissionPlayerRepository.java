package io.github.gyulbbe.entrysubmission.repository;

import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionPlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntrySubmissionPlayerRepository extends JpaRepository<EntrySubmissionPlayerEntity, Long> {
    int deleteByEntrySubmissionSessionId(Long entrySubmissionSessionId);

    long countByEntrySubmissionSessionId(Long entrySubmissionSessionId);

    long countByEntrySubmissionTeamId(Long entrySubmissionTeamId);

    List<EntrySubmissionPlayerEntity> findAllByEntrySubmissionSessionIdOrderByEntrySubmissionTeamIdAscDisplayOrderAscIdAsc(
            Long entrySubmissionSessionId
    );

    List<EntrySubmissionPlayerEntity> findAllByEntrySubmissionTeamIdOrderByDisplayOrderAscIdAsc(Long entrySubmissionTeamId);
}
