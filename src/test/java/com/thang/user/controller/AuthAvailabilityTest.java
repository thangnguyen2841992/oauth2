package com.thang.user.controller;

import com.thang.user.service.user.IUserService;
import com.thang.user.service.user.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.thang.user.model.dto.CreateUserRequest;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthAvailabilityTest {
    @Test void databaseFailureDuringRefreshIsUnavailableNotInvalidToken() {
        var users = mock(IUserService.class);
        when(users.refresh("test-token")).thenThrow(new DataAccessResourceFailureException("offline"));
        var controller = new AuthController(users, mock(SessionService.class), mock(com.thang.user.service.user.JwtService.class), new com.thang.user.service.user.GoogleOAuthState());
        var response = controller.refresh("test-token");
        assertEquals(503, response.getStatusCode().value());
        assertNull(response.getHeaders().getFirst("Set-Cookie"));
    }
    @Test void invalidTokenStillRequiresLogin() {
        var users = mock(IUserService.class);
        when(users.refresh("invalid")).thenThrow(new RuntimeException("Invalid refresh token"));
        var controller = new AuthController(users, mock(SessionService.class), mock(com.thang.user.service.user.JwtService.class), new com.thang.user.service.user.GoogleOAuthState());
        assertEquals(401, controller.refresh("invalid").getStatusCode().value());
        assertEquals(401, controller.refresh(null).getStatusCode().value());
    }
    @Test void databaseFailureDuringLoginIsUnavailable() {
        var users = mock(IUserService.class);
        when(users.login(null)).thenThrow(new DataAccessResourceFailureException("offline"));
        var controller = new AuthController(users, mock(SessionService.class), mock(com.thang.user.service.user.JwtService.class), new com.thang.user.service.user.GoogleOAuthState());
        assertEquals(503, controller.login(null).getStatusCode().value());
    }
    @Test void registrationReturnsExpectedErrorAndAvailabilityStatus() {
        var users = mock(IUserService.class);
        var request = new CreateUserRequest();
        var controller = new AuthController(users, mock(SessionService.class), mock(com.thang.user.service.user.JwtService.class), new com.thang.user.service.user.GoogleOAuthState());
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày sinh không hợp lệ")).when(users).createUser(request);
        assertEquals(400, controller.register(request).getStatusCode().value());

        doThrow(new DataIntegrityViolationException("duplicate")).when(users).createUser(request);
        var duplicate = controller.register(request);
        assertEquals(409, duplicate.getStatusCode().value());
        assertEquals("Email đã tồn tại", ((java.util.Map<?, ?>) duplicate.getBody()).get("message"));

        doThrow(new DataAccessResourceFailureException("offline")).when(users).createUser(request);
        assertEquals(503, controller.register(request).getStatusCode().value());
    }
}
