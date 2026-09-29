package com.thang.user.config;

import com.thang.user.service.user.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AuthSocketAuthorization implements ChannelInterceptor {
    private final SessionService sessions;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var frame = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (frame == null || frame.getCommand() == null || frame.getCommand() == StompCommand.DISCONNECT) return message;
        if (!(frame.getUser() instanceof JwtAuthenticationToken authentication)) throw denied();
        var jwt = authentication.getToken();
        if (!"access".equals(jwt.getClaimAsString("type")) || jwt.getExpiresAt() == null
                || !jwt.getExpiresAt().isAfter(Instant.now())
                || sessions.deadline(jwt.getSubject(), jwt.getClaimAsString("sessionId"), false) == null) throw denied();
        if (frame.getCommand() == StompCommand.SEND) throw denied();
        if (frame.getCommand() == StompCommand.SUBSCRIBE && !"/user/queue/logout".equals(frame.getDestination())) throw denied();
        return message;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Invalid session or unauthorized socket destination");
    }
}
