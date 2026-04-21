package io.github.gyulbbe.rpsdraft.auth;

public record RpsDraftActor(
        Long userPk,
        String username,
        String role
) {
}
