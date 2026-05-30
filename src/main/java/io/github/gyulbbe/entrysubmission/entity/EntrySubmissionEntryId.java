package io.github.gyulbbe.entrysubmission.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EntrySubmissionEntryId implements Serializable {
    private Long entrySubmissionSessionId;
    private Long entrySubmissionTeamId;
    private Integer setNo;
}
