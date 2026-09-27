package com.thang.user.service.user;

import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenLifetimeTest {
    final JwtService jwt = new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 300000, 604800000);
    final SessionService sessions = mock(SessionService.class);
    final IUserRepository users = mock(IUserRepository.class);
    final TokenService service = new TokenService(jwt, sessions, users);
    User user(String role) {
        var user = new User(); user.setUserId("test-user"); user.setEmail("test@example.com");
        user.setRoleName(role); user.setActive(true); return user;
    }
    @Test void userAndAdminHaveDifferentAbsoluteDeadlines() {
        for (String role : new String[]{"USER", "ADMIN"}) {
            long now = System.currentTimeMillis();
            when(sessions.deadline("test-user", "sid", false)).thenReturn(now + 1800000);
            var response = service.generateToken(user(role), "sid");
            long expected = role.equals("ADMIN") ? 28800000 : 604800000;
            assertEquals(expected, response.getRefreshExpiresAt() - now, 2000);
            assertEquals(300000, response.getAccessExpiresAt() - now, 2000);
            verify(sessions).create(eq("test-user"), eq("sid"), eq(response.getRefresh_token()), eq(response.getRefreshExpiresAt()), eq(role.equals("ADMIN")));
        }
    }
    @Test void rotationPreservesOriginalExpiryAndCapsAccessAtDeadline() {
        var user = user("USER"); long deadline = System.currentTimeMillis() + 45000;
        String old = jwt.generateRefreshToken(user, "sid", deadline);
        when(sessions.isValidSession("test-user", "sid")).thenReturn(true);
        when(sessions.deadline("test-user", "sid", false)).thenReturn(deadline);
        when(users.findByUserId("test-user")).thenReturn(Optional.of(user));
        when(sessions.rotate(eq("test-user"), eq("sid"), eq(old), anyString())).thenAnswer(invocation -> invocation.getArgument(3));
        var response = service.refreshToken(old);
        assertNotEquals(old, response.getRefresh_token());
        assertEquals(jwt.parseAndValidate(old).getExpiration().getTime(), response.getRefreshExpiresAt());
        assertTrue(response.getAccessExpiresAt() <= deadline);
        assertEquals(response.getRefreshExpiresAt(), jwt.parseAndValidate(response.getRefresh_token()).getExpiration().getTime());
    }
    @Test void expiredSessionCannotRefreshAndAccessTokenCannotBeUsedAsRefresh() {
        var user = user("USER");
        assertThrows(IllegalArgumentException.class, () -> service.refreshToken(jwt.generateRefreshToken(user, "sid")));
        assertThrows(IllegalArgumentException.class, () -> service.refreshToken(jwt.generateAccessToken(user, "sid")));
        verifyNoInteractions(users);
        verify(sessions, never()).rotate(anyString(), anyString(), anyString(), anyString());
    }
    @Test void replayRejectionDoesNotIssueTokens() {
        var user = user("USER");
        when(sessions.isValidSession("test-user", "sid")).thenReturn(true);
        when(users.findByUserId("test-user")).thenReturn(Optional.of(user));
        assertThrows(IllegalArgumentException.class, () -> service.refreshToken(jwt.generateRefreshToken(user, "sid")));
    }
}
