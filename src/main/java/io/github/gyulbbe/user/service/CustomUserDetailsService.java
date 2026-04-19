package io.github.gyulbbe.user.service;

import io.github.gyulbbe.user.dto.CustomUserDetails;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUserIdIgnoreCaseAndStatus(userId, "ACTIVE");

        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없거나 비활성 상태입니다: " + userId);
        }

        System.out.println("Found user: " + user.getUserId());
        System.out.println("User type from DB: " + user.getUserType());
        return new CustomUserDetails(user);
    }
}
