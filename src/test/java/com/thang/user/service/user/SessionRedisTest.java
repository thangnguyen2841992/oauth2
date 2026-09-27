package com.thang.user.service.user;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

// Opt-in local integration tests. Never load application.yml or production credentials.
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_REDIS_PORT", matches = "\\d+")
class SessionRedisTest {
    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;
    SessionService sessions;
    String user;
    @BeforeAll static void connect() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("AUTH_TEST_REDIS_PORT")));
        factory.afterPropertiesSet(); factory.start(); redis = new StringRedisTemplate(factory);
    }
    @AfterAll static void close() { factory.destroy(); }
    @BeforeEach void setup() { sessions = new SessionService(redis); user = "test-" + UUID.randomUUID(); }
    @AfterEach void cleanup() { redis.delete(SessionService.key(user)); }
    @Test void ttlMatchesAbsoluteDeadlineForUserAndAdmin() {
        for (boolean admin : new boolean[]{false, true}) {
            long lifetime = admin ? 28800000L : 604800000L;
            long now = System.currentTimeMillis();
            sessions.create(user, "sid", "old", now + lifetime, admin);
            long ttl = redis.getExpire(SessionService.key(user), TimeUnit.MILLISECONDS);
            assertTrue(ttl > lifetime - 2000 && ttl <= lifetime);
            long deadline = sessions.deadline(user, "sid", false);
            assertEquals(admin ? 1800000L : lifetime, deadline - now, 2000);
        }
    }
    @Test void concurrentRefreshesReturnOneSuccessorAndReplayRevokes() throws Exception {
        sessions.create(user, "sid", "old", System.currentTimeMillis() + 60000, false);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var start = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String next = "next-" + i;
                results.add(pool.submit(() -> { start.await(); return sessions.rotate(user, "sid", "old", next); }));
            }
            start.countDown(); Set<String> successors = new HashSet<>();
            for (var result : results) successors.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(1, successors.size()); assertFalse(successors.contains(null));
            String next = successors.iterator().next();
            assertEquals(next, sessions.rotate(user, "sid", "old", "ignored"));
            assertEquals(next, sessions.rotate(user, "sid", next, "also-ignored"));
            redis.opsForHash().put(SessionService.key(user), "rotateAfter", "1");
            assertNull(sessions.rotate(user, "sid", "old", "replay"));
            assertNull(sessions.getSession(user));
        } finally { pool.shutdownNow(); }
    }
    @Test void pollingAndRefreshDoNotExtendIdleButActivityDoesWithinAbsoluteLimit() {
        long expires = System.currentTimeMillis() + 90000;
        sessions.create(user, "sid", "old", expires, true);
        long idle = System.currentTimeMillis() + 30000;
        redis.opsForHash().put(SessionService.key(user), "idle", Long.toString(idle));
        assertEquals(idle, sessions.deadline(user, "sid", false));
        sessions.rotate(user, "sid", "old", "new");
        assertEquals(idle, sessions.deadline(user, "sid", false));
        assertEquals(expires, sessions.deadline(user, "sid", true));
        assertTrue(redis.getExpire(SessionService.key(user), TimeUnit.MILLISECONDS) <= 90000);
    }
    @Test void expiredIdleCannotBeRevived() {
        sessions.create(user, "sid", "old", System.currentTimeMillis()+60000, true);
        redis.opsForHash().put(SessionService.key(user), "idle", "1");
        assertNull(sessions.deadline(user, "sid", true));
        assertNull(sessions.rotate(user, "sid", "old", "new"));
    }
    @Test void staleLogoutCannotDeleteNewSessionAndLogoutRevokesRefresh() {
        sessions.create(user, "new-sid", "new", System.currentTimeMillis()+60000, false);
        sessions.removeSession(user, "old-sid");
        assertTrue(sessions.isValidSession(user, "new-sid"));
        sessions.removeSession(user, "new-sid");
        assertFalse(sessions.isValidSession(user, "new-sid"));
        assertNull(sessions.rotate(user, "new-sid", "new", "another"));
    }
}
