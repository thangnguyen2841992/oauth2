package com.thang.user.service.user;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class GoogleOAuthStateTest {
    private final GoogleOAuthState states = new GoogleOAuthState();
    @Test void stateIsUnpredictableAndUrlSafe() {
        String state = states.issue();
        assertTrue(state.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(state, states.issue());
        assertDoesNotThrow(() -> states.verify(state, state));
    }
    @Test void callbacksWithoutMatchingBrowserStateAreRejected() {
        String state = states.issue();
        assertThrows(ResponseStatusException.class, () -> states.verify(null, state));
        assertThrows(ResponseStatusException.class, () -> states.verify(state, null));
        assertThrows(ResponseStatusException.class, () -> states.verify(states.issue(), state));
    }
}
