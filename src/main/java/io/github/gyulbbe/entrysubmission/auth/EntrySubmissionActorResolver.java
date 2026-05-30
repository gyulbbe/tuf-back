package io.github.gyulbbe.entrysubmission.auth;

import io.github.gyulbbe.user.dto.CustomUserDetails;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntrySubmissionActorResolver {

    private final UserRepository userRepository;

    public EntrySubmissionActor resolveRequired() {
        EntrySubmissionActor actor = resolveOptional();
        if (actor == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }
        return actor;
    }

    public EntrySubmissionActor resolveOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            Long userPk = customUserDetails.getUserPk();
            if (userPk != null) {
                return new EntrySubmissionActor(
                        userPk,
                        customUserDetails.getUsername(),
                        authentication.getAuthorities().iterator().next().getAuthority()
                );
            }

            UserEntity user = userRepository.findByUserIdIgnoreCaseAndStatus(customUserDetails.getUsername(), "ACTIVE");
            if (user == null) {
                throw new IllegalArgumentException("Authenticated user could not be found.");
            }

            return new EntrySubmissionActor(user.getId(), user.getUserId(), user.getUserType());
        }

        throw new IllegalArgumentException("Authenticated user could not be resolved.");
    }
}
