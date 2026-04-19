package io.github.gyulbbe.draft.auth;

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
public class DraftActorResolver {

    private final UserRepository userRepository;

    public AuthActor resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
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
                throw new IllegalArgumentException("로그인 사용자를 찾을 수 없습니다.");
            }
            return new AuthActor(user.getId(), user.getUserId(), user.getUserType());
        }

        throw new IllegalArgumentException("로그인 사용자를 확인할 수 없습니다.");
    }
}
