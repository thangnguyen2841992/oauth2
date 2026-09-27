package com.thang.user.controller;

import com.thang.user.model.dto.TokenUserResponse;
import com.thang.user.service.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Base64;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthCookieTest {
    final IUserService users = mock(IUserService.class);
    final SessionService sessions = mock(SessionService.class);
    final JwtService jwt = new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 300000, 604800000);
    final AuthController controller = new AuthController(users, sessions, jwt);
    TokenUserResponse tokens() {
        return TokenUserResponse.builder().access_token("access").refresh_token("refresh")
            .accessExpiresAt(System.currentTimeMillis() + 300000)
            .refreshExpiresAt(System.currentTimeMillis() + 90000).build();
    }
    void assertCookies(java.util.List<String> cookies) {
        assertEquals(2, cookies.size());
        String refresh = cookies.stream().filter(s -> s.startsWith("refreshToken=")).findFirst().orElseThrow();
        var matcher = java.util.regex.Pattern.compile("Max-Age=(\\d+)").matcher(refresh);
        assertTrue(matcher.find()); long age = Long.parseLong(matcher.group(1));
        assertTrue(age > 85 && age <= 90, refresh);
        assertTrue(refresh.contains("HttpOnly")); assertTrue(refresh.contains("SameSite=Lax"));
    }
    @Test void loginAndRefreshCookiesUseRemainingLifetime() {
        when(users.login(null)).thenReturn(tokens());
        assertCookies(controller.login(null).getHeaders().get("Set-Cookie"));
        when(users.refresh("old")).thenReturn(tokens());
        assertCookies(controller.refresh("old").getHeaders().get("Set-Cookie"));
    }
    @Test void googleSetupUsesSameCookies() throws Exception {
        when(users.setupGooglePassword(null)).thenReturn(tokens());
        var response = new MockHttpServletResponse(); controller.setupGooglePassword(null, response);
        assertCookies(response.getHeaders("Set-Cookie"));
    }
    @Test void logoutWorksWithRefreshOnlyAndCannotRemoveAnotherSession() {
        var user = new com.thang.user.model.entity.User(); user.setUserId("user"); user.setRoleName("USER");
        controller.logout(jwt.generateRefreshToken(user, "old-session"), null);
        verify(sessions).removeSession("user", "old-session");
        verify(sessions, never()).removeSession(anyString());
    }
    @Test void checkLoginWithoutAccessReturns401SoClientCanRefresh() {
        assertEquals(401, controller.checkLogin(null).getStatusCode().value());
    }
}
