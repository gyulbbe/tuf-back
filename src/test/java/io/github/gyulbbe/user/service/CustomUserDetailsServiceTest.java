package io.github.gyulbbe.user.service;

import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsername_throwsDisabledException_forInactiveUser() {
        when(userRepository.findByUserIdIgnoreCase("inactive"))
                .thenReturn(UserEntity.builder()
                        .id(1L)
                        .userId("inactive")
                        .password("encoded-password")
                        .userType("ROLE_USER")
                        .status("INACTIVE")
                        .build());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("inactive"))
                .isInstanceOf(DisabledException.class)
                .hasMessage("비활성화된 계정입니다.");
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFoundException_whenMissing() {
        when(userRepository.findByUserIdIgnoreCase("missing")).thenReturn(null);

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }
}
