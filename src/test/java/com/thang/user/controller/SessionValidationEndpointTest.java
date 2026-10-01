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
        when(sessions.deadline("user", "sid", false)).thenReturn(System.currentTimeMillis() + 10000);
        var controller = new AuthController(mock(IUserService.class), sessions, jwt, new GoogleOAuthState());

        assertEquals(401, controller.validateSession(null).getStatusCode().value());
        assertEquals(401, controller.validateSession("valid-token").getStatusCode().value());
        assertEquals(204, controller.validateSession("Bearer valid-token").getStatusCode().value());
        when(sessions.deadline("user", "sid", false)).thenReturn(null);
        assertEquals(401, controller.validateSession("Bearer valid-token").getStatusCode().value());
    }
}
