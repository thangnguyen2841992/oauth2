package com.thang.user.controller;

import com.thang.user.service.user.GoogleOAuthState;
import com.thang.user.service.user.IUserService;
import com.thang.user.service.user.JwtService;
import com.thang.user.service.user.SessionService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SessionValidationEndpointTest {
    @Test void onlySignedAccessTokenWithActiveSessionIsAccepted() {
        JwtService jwt = mock(JwtService.class);
        SessionService sessions = mock(SessionService.class);
        Claims claims = mock(Claims.class);
        when(jwt.parseAndValidate("valid-token")).thenReturn(claims);
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.get("sessionId", String.class)).thenReturn("sid");
        when(claims.getSubject()).thenReturn("user");
        when(claims.get("roles",java.util.List.class)).thenReturn(java.util.List.of("USER"));
        when(sessions.roleMatches("user","sid",java.util.List.of("USER"))).thenReturn(true);
        when(sessions.accessDeadline("user","sid",java.util.List.of("USER"),false)).thenReturn(System.currentTimeMillis()+10000);
        var credentials=mock(com.nihongo.security.InternalCredentials.class);when(credentials.matches("internal")).thenReturn(true);
        var controller = new AuthController(mock(IUserService.class), sessions, jwt, new GoogleOAuthState(),credentials);

        assertEquals(401, controller.validateSession(null,"internal").getStatusCode().value());
        assertEquals(401, controller.validateSession("valid-token","internal").getStatusCode().value());
        assertEquals(401, controller.validateSession("Bearer valid-token","wrong").getStatusCode().value());
        assertEquals(204, controller.validateSession("Bearer valid-token","internal").getStatusCode().value());
        when(sessions.accessDeadline("user","sid",java.util.List.of("USER"),false)).thenReturn(null);
        assertEquals(401, controller.validateSession("Bearer valid-token","internal").getStatusCode().value());
    }
}
