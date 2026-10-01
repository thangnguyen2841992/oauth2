package com.thang.user.service.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** One active session per user, kept only for the lifetime of this JVM. */
@Service
public class SessionService {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    @Value("${jwt.admin-idle-timeout:1800000}")
    private long adminIdleTimeout = 1800000;

    public void create(String userId, String sid, String token, long expires, boolean admin) {
        long now = System.currentTimeMillis();
        sessions.put(userId, new Session(sid, token, hash(token), null, 0, expires,
                admin ? Math.min(expires, now + adminIdleTimeout) : expires, admin));
    }

    public String getSession(String userId) {
        Session session = sessions.get(userId);
        if (session == null) return null;
        if (session.expires <= System.currentTimeMillis()) {
            sessions.remove(userId, session);
            return null;
        }
        return session.sid;
    }

    public void removeSession(String userId) {
        if (userId != null) sessions.remove(userId);
    }

    public void removeSession(String userId, String sid) {
        if (userId != null && sid != null) sessions.computeIfPresent(userId,
                (ignored, session) -> sid.equals(session.sid) ? null : session);
    }

    public boolean isValidSession(String userId, String sid) {
        return deadline(userId, sid, false) != null;
    }

    public Long deadline(String userId, String sid, boolean activity) {
        if (userId == null || sid == null) return null;
        long now = System.currentTimeMillis();
        AtomicReference<Long> result = new AtomicReference<>();
        sessions.computeIfPresent(userId, (ignored, session) -> {
            if (!sid.equals(session.sid)) return session;
            if (session.expires <= now || session.idle <= now) return null;
            long idle = activity && session.admin ? Math.min(session.expires, now + adminIdleTimeout) : session.idle;
            result.set(Math.min(session.expires, idle));
            return idle == session.idle ? session : session.withIdle(idle);
        });
        return result.get();
    }

    public String rotate(String userId, String sid, String oldToken, String newToken) {
        if (userId == null || sid == null || oldToken == null || newToken == null) return null;
        long now = System.currentTimeMillis();
        String presented = hash(oldToken);
        AtomicReference<String> result = new AtomicReference<>();
        sessions.computeIfPresent(userId, (ignored, session) -> {
            if (!sid.equals(session.sid)) return session;
            if (session.expires <= now || session.idle <= now) return null;
            if (presented.equals(session.hash)) {
                if (now < session.rotateAfter) {
                    result.set(session.token);
                    return session;
                }
                result.set(newToken);
                return session.rotated(newToken, hash(newToken), session.hash, now + 10000);
            }
            if (now < session.rotateAfter && presented.equals(session.previousHash)) {
                result.set(session.token);
                return session;
            }
            // A stale refresh token outside the overlap revokes the session.
            return null;
        });
        return result.get();
    }

    @Scheduled(fixedDelay = 300000)
    public void removeExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.forEach((userId, session) -> {
            if (session.expires <= now || session.idle <= now) sessions.remove(userId, session);
        });
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Session(String sid, String token, String hash, String previousHash,
                           long rotateAfter, long expires, long idle, boolean admin) {
        Session withIdle(long nextIdle) {
            return new Session(sid, token, hash, previousHash, rotateAfter, expires, nextIdle, admin);
        }
        Session rotated(String nextToken, String nextHash, String oldHash, long nextRotateAfter) {
            return new Session(sid, nextToken, nextHash, oldHash, nextRotateAfter, expires, idle, admin);
        }
    }
}
