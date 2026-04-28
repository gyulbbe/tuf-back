package io.github.gyulbbe.draft.auth;

public record AuthActor(
        Long userPk,
        String username,
        String role
) {
}
