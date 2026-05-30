package io.github.gyulbbe.entrysubmission.dto;

import lombok.Data;

import java.util.List;

@Data
public class EntrySubmissionSubmitRequestDto {
    private List<EntrySubmissionEntryRequestDto> entries;
}
