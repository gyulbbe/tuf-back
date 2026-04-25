package io.github.gyulbbe.jwt;

import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.common.error.ApiErrorResponseWriter;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import io.github.gyulbbe.user.entity.UserEntity;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring("Bearer".length()).trim();
        if (token.isBlank()) {
            ApiErrorResponseWriter.write(response, ApiErrorCode.AUTH_REQUIRED);
            return;
        }

        try {
            authenticateToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            ApiErrorResponseWriter.write(response, ApiErrorCode.AUTH_REQUIRED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateToken(String token) {
        if (Boolean.TRUE.equals(jwtUtil.isExpired(token))) {
            return;
        }

        String username = jwtUtil.getUsername(token);
        String role = jwtUtil.getRole(token);
        Long userPk = jwtUtil.getUserPk(token);

        UserEntity userEntity = UserEntity.builder()
                .id(userPk)
                .userId(username)
                .password("tempassword")
                .userType(role)
                .build();

        CustomUserDetails customUserDetails = new CustomUserDetails(userEntity);

        //스프링 시큐리티 인증 토큰 생성
        Authentication authToken = new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());
        
        //세션 사용자 등록
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
