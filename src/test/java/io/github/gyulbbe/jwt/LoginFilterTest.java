package io.github.gyulbbe.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LoginFilterTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JWTUtil jwtUtil;

    private LoginFilter loginFilter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        loginFilter = new LoginFilter(authenticationManager, jwtUtil);
        objectMapper = new ObjectMapper();
    }

    @Test
    void unsuccessfulAuthentication_returnsInactiveMessage_forDisabledException() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        loginFilter.unsuccessfulAuthentication(
                new MockHttpServletRequest(),
                response,
                new DisabledException("비활성화된 계정입니다.")
        );

        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.get("message")).isEqualTo("비활성화된 계정입니다.");
        assertThat(body.get("data")).isNull();
        assertThat(body.get("errorCode")).isEqualTo("AUTH_ACCOUNT_INACTIVE");
    }

    @Test
    void unsuccessfulAuthentication_returnsCredentialMessage_forBadCredentials() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        loginFilter.unsuccessfulAuthentication(
                new MockHttpServletRequest(),
                response,
                new BadCredentialsException("Bad credentials")
        );

        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.get("message")).isEqualTo("아이디 또는 비밀번호가 올바르지 않습니다.");
        assertThat(body.get("data")).isNull();
        assertThat(body.get("errorCode")).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void unsuccessfulAuthentication_returnsCredentialMessage_forUsernameNotFound() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        loginFilter.unsuccessfulAuthentication(
                new MockHttpServletRequest(),
                response,
                new UsernameNotFoundException("missing")
        );

        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.get("message")).isEqualTo("아이디 또는 비밀번호가 올바르지 않습니다.");
        assertThat(body.get("data")).isNull();
        assertThat(body.get("errorCode")).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void unsuccessfulAuthentication_returnsValidationError_forInvalidLoginRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        loginFilter.unsuccessfulAuthentication(
                new MockHttpServletRequest(),
                response,
                new InvalidLoginRequestException("invalid")
        );

        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body.get("status")).isEqualTo(400);
        assertThat(body.get("message")).isEqualTo("아이디와 비밀번호를 입력해주세요.");
        assertThat(body.get("data")).isNull();
        assertThat(body.get("errorCode")).isEqualTo("VALIDATION_FAILED");
    }
}
