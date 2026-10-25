package com.thang.user.service.user;

import com.thang.user.repository.AuthSessionRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionConfirmationCacheTest {
    static AuthSessionRepository.Deadline row(String sid, String role) {
        var d = mock(AuthSessionRepository.Deadline.class);
        long later = System.currentTimeMillis() + 3_600_000;
        when(d.getSid()).thenReturn(sid); when(d.getRole()).thenReturn(role); when(d.getActive()).thenReturn(true);
        when(d.getExpiresAt()).thenReturn(later); when(d.getIdleUntil()).thenReturn(later);
        return d;
    }

    @Test void confirmedSessionIsReusedBrieflyAndDroppedOnLogout() {
        var store = mock(AuthSessionRepository.class);
        var sessions = new SessionService(store, mock(SessionTokenCipher.class));
        var current = row("sid-1", "USER");
        when(store.deadline("u1")).thenReturn(Optional.of(current));

        assertTrue(sessions.roleMatches("u1", "sid-1", List.of("USER")));
        assertTrue(sessions.roleMatches("u1", "sid-1", List.of("USER")));
        verify(store, times(1)).deadline("u1");

        assertFalse(sessions.roleMatches("u1", "sid-1", List.of("ADMIN")), "a different role is never answered from the cache");
        assertFalse(sessions.roleMatches("u1", "sid-other", List.of("USER")));

        sessions.removeSession("u1", "sid-1");
        when(store.deadline("u1")).thenReturn(Optional.empty());
        assertFalse(sessions.roleMatches("u1", "sid-1", List.of("USER")), "logout takes effect immediately on this instance");
    }

    @Test void gatewayAndServiceValidationsShareOneLookup() {
        var store = mock(AuthSessionRepository.class);
        var sessions = new SessionService(store, mock(SessionTokenCipher.class));
        var current = row("sid-1", "STAFF");
        when(store.deadline("u2")).thenReturn(Optional.of(current));
        assertNull(sessions.confirmedDeadline("u2", "sid-1", List.of("STAFF")));
        Long deadline = sessions.accessDeadline("u2", "sid-1", List.of("STAFF"), false);
        assertNotNull(deadline);
        assertEquals(deadline, sessions.confirmedDeadline("u2", "sid-1", List.of("STAFF")));
        assertNull(sessions.confirmedDeadline("u2", "sid-1", List.of("ADMIN")));
        sessions.removeSession("u2");
        assertNull(sessions.confirmedDeadline("u2", "sid-1", List.of("STAFF")));
    }
}
