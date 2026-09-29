package com.thang.user.config;

import com.thang.user.service.user.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSocketAuthorizationTest {
    SessionService sessions = mock(SessionService.class);
    AuthSocketAuthorization authorization = new AuthSocketAuthorization(sessions);

    org.springframework.messaging.Message<?> frame(StompCommand command, String destination, Instant expiry) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setUser(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "HS512")
                .subject("owner").claim("type", "access").claim("sessionId", "sid").expiresAt(expiry).build()));
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test void allowsOnlyAnActiveSessionToSubscribeToItsLogoutQueue() {
        when(sessions.deadline("owner", "sid", false)).thenReturn(System.currentTimeMillis()+60000);
        var message = frame(StompCommand.SUBSCRIBE, "/user/queue/logout", Instant.now().plusSeconds(60));
        assertSame(message, authorization.preSend(message, null));
        when(sessions.deadline("owner", "sid", false)).thenReturn(null);
        assertThrows(AccessDeniedException.class, () -> authorization.preSend(message, null));
    }

    @Test void rejectsClientPublishingAndOtherDestinations() {
        when(sessions.deadline("owner", "sid", false)).thenReturn(System.currentTimeMillis()+60000);
        for (var command : new StompCommand[]{StompCommand.SEND, StompCommand.SUBSCRIBE}) {
            assertThrows(AccessDeniedException.class, () -> authorization.preSend(
                    frame(command, "/queue/logout-user", Instant.now().plusSeconds(60)), null));
        }
        assertThrows(AccessDeniedException.class, () -> authorization.preSend(
                frame(StompCommand.CONNECT, "/user/queue/logout", Instant.now().minusSeconds(1)), null));
    }
}
