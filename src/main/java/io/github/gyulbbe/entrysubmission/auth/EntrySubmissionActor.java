package io.github.gyulbbe.entrysubmission.auth;

public record EntrySubmissionActor(
        Long userPk,
        String username,
        String role
) {
}
