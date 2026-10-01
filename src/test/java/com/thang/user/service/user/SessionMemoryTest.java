package com.thang.user.service.user;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryTest {
    @Test void replacementLogoutAndExpiry() {
        SessionService sessions = new SessionService();
        long expires = System.currentTimeMillis() + 60000;
        sessions.create("user", "first", "old", expires, false);
        sessions.create("user", "second", "new", expires, false);
        sessions.removeSession("user", "first");
        assertEquals("second", sessions.getSession("user"));
        assertFalse(sessions.isValidSession("user", "first"));
        sessions.removeSession("user", "second");
        assertFalse(sessions.isValidSession("user", "second"));
        sessions.create("expired", "sid", "token", System.currentTimeMillis() - 1, false);
        assertNull(sessions.deadline("expired", "sid", false));
        assertNull(sessions.getSession("expired"));
    }

    @Test void concurrentRefreshesReceiveSameSuccessorAndReplayRevokes() throws Exception {
        SessionService sessions = new SessionService();
        sessions.create("user", "sid", "old", System.currentTimeMillis() + 60000, false);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var start = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String next = "next-" + i;
                results.add(pool.submit(() -> {
                    start.await();
                    return sessions.rotate("user", "sid", "old", next);
                }));
            }
            start.countDown();
            Set<String> successors = new HashSet<>();
            for (var result : results) successors.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(1, successors.size());
            String next = successors.iterator().next();
            assertNotNull(next);
            assertEquals(next, sessions.rotate("user", "sid", "old", "ignored"));
            assertEquals(next, sessions.rotate("user", "sid", next, "also-ignored"));
            assertNull(sessions.rotate("user", "sid", "stale", "replay"));
            assertFalse(sessions.isValidSession("user", "sid"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void activityExtendsAdminIdleButPollingDoesNot() {
        SessionService sessions = new SessionService();
        ReflectionTestUtils.setField(sessions, "adminIdleTimeout", 150);
        long expires = System.currentTimeMillis() + 3000;
        sessions.create("admin", "sid", "old", expires, true);
        long initial = sessions.deadline("admin", "sid", false);
        assertNotNull(initial);
        sessions.rotate("admin", "sid", "old", "new");
        assertEquals(initial, sessions.deadline("admin", "sid", false));
        try { Thread.sleep(25); } catch (InterruptedException e) { Thread.currentThread().interrupt(); fail(e); }
        assertTrue(sessions.deadline("admin", "sid", true) > initial);
        assertTrue(sessions.deadline("admin", "sid", false) <= expires);
    }

    @Test void expiredIdleCannotBeRevived() throws Exception {
        SessionService sessions = new SessionService();
        ReflectionTestUtils.setField(sessions, "adminIdleTimeout", 20);
        sessions.create("admin", "sid", "old", System.currentTimeMillis() + 60000, true);
        Thread.sleep(40);
        assertNull(sessions.deadline("admin", "sid", true));
        assertNull(sessions.rotate("admin", "sid", "old", "new"));
    }
}
