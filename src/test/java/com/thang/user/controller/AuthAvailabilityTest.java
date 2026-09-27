package com.thang.user.controller;

import com.thang.user.service.user.IUserService;
import com.thang.user.service.user.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthAvailabilityTest {
    @Test void redisFailureDuringRefreshIsUnavailableNotInvalidToken() {
        var users = mock(IUserService.class);
        when(users.refresh("test-token")).thenThrow(new RedisConnectionFailureException("offline"));
        var controller = new AuthController(users, mock(SessionService.class), mock(com.thang.user.service.user.JwtService.class));
        var response = controller.refresh("test-token");
        assertEquals(503, response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst("Set-Cookie"));
    }
    @Test void invalidTokenStillRequiresLogin() {
        var users = mock(IUserService.class);
        when(users.refresh("invalid")).thenThrow(new RuntimeException("Invalid refresh token"));
        var controller = new AuthController(users, mock(SessionService.class), mock(com.thang.user.service.user.JwtService.class));
        assertEquals(401, controller.refresh("invalid").getStatusCode().value());
        assertEquals(401, controller.refresh(null).getStatusCode().value());
    }
    @Test void redisFailureDuringLoginIsUnavailable() {
        var users = mock(IUserService.class);
        when(users.login(null)).thenThrow(new RedisConnectionFailureException("offline"));
        var controller = new AuthController(users, mock(SessionService.class), mock(com.thang.user.service.user.JwtService.class));
        assertEquals(503, controller.login(null).getStatusCode().value());
    }
}
