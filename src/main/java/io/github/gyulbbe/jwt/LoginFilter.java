package io.github.gyulbbe.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.common.error.ApiErrorResponseWriter;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private static final String INVALID_LOGIN_REQUEST_MESSAGE = "아이디와 비밀번호를 입력해주세요.";
    private static final TypeReference<Map<String, String>> LOGIN_REQUEST_TYPE = new TypeReference<>() {
    };

    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> data;
        try {
            data = objectMapper.readValue(request.getInputStream(), LOGIN_REQUEST_TYPE);
        } catch (IOException e) {
            throw new InvalidLoginRequestException(INVALID_LOGIN_REQUEST_MESSAGE, e);
        }

        String username = data.get("username");
        String password = data.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new InvalidLoginRequestException(INVALID_LOGIN_REQUEST_MESSAGE);
        }

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(username, password, null);

        return authenticationManager.authenticate(authenticationToken);
    }

    @Override
    protected void successfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            Authentication authentication
    ) throws IOException, ServletException {
        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        String username = customUserDetails.getUsername();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();

        String role = auth.getAuthority();
        String photo = customUserDetails.getPhoto();
        Long userPk = customUserDetails.getUserPk();

        String token = jwtUtil.createJwt(username, userPk, role, photo, 60 * 60 * 1000L * 12);

        response.addHeader("Authorization", "Bearer " + token);
    }

    @Override
    protected void unsuccessfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failed
    ) throws IOException, ServletException {
        ApiErrorCode errorCode = resolveLoginErrorCode(failed);
        String message = failed instanceof InvalidLoginRequestException
                ? INVALID_LOGIN_REQUEST_MESSAGE
                : errorCode.getDefaultMessage();
        ApiErrorResponseWriter.write(response, errorCode, message);
    }

    private ApiErrorCode resolveLoginErrorCode(AuthenticationException failed) {
        if (failed instanceof InvalidLoginRequestException) {
            return ApiErrorCode.VALIDATION_FAILED;
        }
        if (failed instanceof DisabledException) {
            return ApiErrorCode.AUTH_ACCOUNT_INACTIVE;
        }
        if (failed instanceof BadCredentialsException || failed instanceof UsernameNotFoundException) {
            return ApiErrorCode.AUTH_INVALID_CREDENTIALS;
        }
        return ApiErrorCode.AUTH_INVALID_CREDENTIALS;
    }
}
