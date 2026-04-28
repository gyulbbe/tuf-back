package io.github.gyulbbe.draft.ws;

import io.github.gyulbbe.jwt.JWTUtil;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import io.github.gyulbbe.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftStompAuthChannelInterceptor implements ChannelInterceptor {

    private final JWTUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        Authentication authentication = resolveAuthentication(accessor);
        if (authentication != null) {
            accessor.setUser(authentication);
        }

        return message;
    }

    private Authentication resolveAuthentication(StompHeaderAccessor accessor) {
        String authorization = readAuthorizationHeader(accessor);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            return null;
        }

        try {
            if (Boolean.TRUE.equals(jwtUtil.isExpired(token))) {
                return null;
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
            return new UsernamePasswordAuthenticationToken(
                    customUserDetails,
                    null,
                    customUserDetails.getAuthorities()
            );
        } catch (RuntimeException e) {
            log.debug("ignored invalid draft websocket token. reason={}", e.getMessage());
            return null;
        }
    }

    private String readAuthorizationHeader(StompHeaderAccessor accessor) {
        String lowerCaseHeader = accessor.getFirstNativeHeader("authorization");
        if (lowerCaseHeader != null) {
            return lowerCaseHeader;
        }
        return accessor.getFirstNativeHeader("Authorization");
    }
}
