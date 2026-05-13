package io.github.gyulbbe.draft.ws;

import io.github.gyulbbe.jwt.JWTUtil;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DraftStompAuthChannelInterceptorTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void connectAuthorizationHeaderSetsAuthenticatedPrincipal() {
        JWTUtil jwtUtil = new JWTUtil(SECRET);
        DraftStompAuthChannelInterceptor interceptor = new DraftStompAuthChannelInterceptor(jwtUtil);
        String token = jwtUtil.createJwt("draft-user", 77L, "ROLE_USER", null, 60_000L);

        Message<?> result = interceptor.preSend(
                connectMessage("Bearer " + token),
                mock(MessageChannel.class)
        );

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isInstanceOf(Authentication.class);

        Authentication authentication = (Authentication) accessor.getUser();
        assertThat(authentication.getName()).isEqualTo("draft-user");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void connectWithoutAuthorizationHeaderLeavesPrincipalEmpty() {
        DraftStompAuthChannelInterceptor interceptor = new DraftStompAuthChannelInterceptor(new JWTUtil(SECRET));

        Message<?> result = interceptor.preSend(
                connectMessage(null),
                mock(MessageChannel.class)
        );

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isNull();
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
