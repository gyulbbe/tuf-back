package io.github.gyulbbe.draft.auth;

import io.github.gyulbbe.user.dto.CustomUserDetails;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class DraftActorResolver {

    private final UserRepository userRepository;

    public AuthActor resolve() {
        return resolve(SecurityContextHolder.getContext().getAuthentication());
    }

    public AuthActor resolve(Principal principal) {
        if (principal instanceof Authentication authentication) {
            return resolve(authentication);
        }

        throw new IllegalArgumentException("Authenticated user could not be resolved.");
    }

    public AuthActor resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            Long userPk = customUserDetails.getUserPk();
            if (userPk != null) {
                return new AuthActor(
                        userPk,
                        customUserDetails.getUsername(),
                        authentication.getAuthorities().iterator().next().getAuthority()
                );
            }

            UserEntity user = userRepository.findByUserIdIgnoreCaseAndStatus(customUserDetails.getUsername(), "ACTIVE");
            if (user == null) {
                throw new IllegalArgumentException("Authenticated user could not be found.");
            }
            return new AuthActor(user.getId(), user.getUserId(), user.getUserType());
        }

        throw new IllegalArgumentException("Authenticated user could not be resolved.");
    }
}
