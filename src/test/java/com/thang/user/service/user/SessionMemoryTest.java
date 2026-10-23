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

    @Test void learnerIdleIsExtendedByRefreshUpToTheAbsoluteDeadline() throws Exception {
        SessionService sessions = new SessionService();
        ReflectionTestUtils.setField(sessions, "userIdleTimeout", 100);
        long expires = System.currentTimeMillis() + 60000;
        sessions.create("user", "sid", "old", expires, "USER");
        long initial = sessions.deadline("user", "sid", false);
        assertTrue(initial < expires, "a learner session starts with the idle limit");
        Thread.sleep(30);
        assertEquals("new", sessions.rotate("user", "sid", "old", "new"));
        assertTrue(sessions.deadline("user", "sid", false) > initial, "using the site (refresh) extends the idle limit");
        assertTrue(sessions.deadline("user", "sid", false) <= expires);
        Thread.sleep(150);
        assertNull(sessions.deadline("user", "sid", false), "an unused learner session expires after the idle limit");
    }

    @Test void staffIdleIsExtendedByInteractionsButNotByRefresh() throws Exception {
        SessionService sessions = new SessionService();
        ReflectionTestUtils.setField(sessions, "staffIdleTimeout", 150);
        long expires = System.currentTimeMillis() + 60000;
        sessions.create("staff", "sid", "old", expires, "STAFF");
        long initial = sessions.deadline("staff", "sid", false);
        Thread.sleep(30);
        sessions.rotate("staff", "sid", "old", "new");
        assertEquals(initial, sessions.deadline("staff", "sid", false), "an open tab refreshing tokens does not keep staff signed in");
        assertTrue(sessions.deadline("staff", "sid", true) > initial);
    }

    @Test void previousRefreshTokenIsAnsweredWithinTheGraceWindowOnly() throws Exception {
        SessionService sessions = new SessionService();
        ReflectionTestUtils.setField(sessions, "reuseGrace", 50);
        sessions.create("user", "sid", "old", System.currentTimeMillis() + 60000, "USER");
        assertEquals("new", sessions.rotate("user", "sid", "old", "new"));
        assertEquals("new", sessions.rotate("user", "sid", "old", "retry"), "a retried refresh gets the same successor");
        Thread.sleep(80);
        assertNull(sessions.rotate("user", "sid", "old", "late"), "replay after the window revokes the session");
        assertFalse(sessions.isValidSession("user", "sid"));
    }
}
