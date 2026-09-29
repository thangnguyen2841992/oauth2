package com.thang.user.controller;

import com.thang.user.model.dto.GoogleLoginResponse;
import com.thang.user.service.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleLoginTest {
    IUserService users = mock(IUserService.class);
    GoogleOAuthState state = new GoogleOAuthState();
    AuthController controller = new AuthController(users, mock(SessionService.class), mock(JwtService.class), state);

    @Test void loginBindsAStateCookieToTheRedirect() throws Exception {
        ReflectionTestUtils.setField(controller, "googleClientId", "client-id");
        ReflectionTestUtils.setField(controller, "googleRedirectUri", "http://localhost/api/auth/callbackGoogle");
        var response = new MockHttpServletResponse();
        controller.googleLogin(response);
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Lax"));
        String value = cookie.substring(cookie.indexOf('=')+1, cookie.indexOf(';'));
        assertTrue(response.getRedirectedUrl().contains("state="+value));
    }

    @Test void rejectsAnUnboundCallbackBeforeExchangingTheCode() {
        assertThrows(ResponseStatusException.class, () -> controller.callbackGoogle("code", state.issue(), null, new MockHttpServletResponse()));
        verifyNoInteractions(users);
    }

    @Test void acceptsMatchingStateAndExpiresIt() throws Exception {
        String value = state.issue();
        when(users.loginWithGoogle("code")).thenReturn(GoogleLoginResponse.builder()
                .status("SET_PASSWORD").setupToken("setup-token").email("user@example.com").build());
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:5173");
        var response = new MockHttpServletResponse();
        controller.callbackGoogle("code", value, value, response);
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
        assertTrue(response.getRedirectedUrl().contains("setup-password?token=setup-token"));
        verify(users).loginWithGoogle("code");
    }
}
