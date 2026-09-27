package com.thang.user.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final StringRedisTemplate redis;
    @Value("${jwt.admin-idle-timeout:1800000}") private long adminIdleTimeout = 1800000;
    public static String key(String userId) { return "auth:session:v2:" + userId; }
    static final String VALID = """
        local sid = redis.call('HGET', KEYS[1], 'sid')
        if not sid or sid ~= ARGV[1] then return nil end
        local expires = tonumber(redis.call('HGET', KEYS[1], 'expires'))
        local idle = tonumber(redis.call('HGET', KEYS[1], 'idle'))
        local now = tonumber(ARGV[2])
        if expires <= now or idle <= now then redis.call('DEL', KEYS[1]); return nil end
        """;
    public void create(String userId, String sid, String token, long expires, boolean admin) {
        long now = System.currentTimeMillis();
        String script = """
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'sid', ARGV[1], 'token', ARGV[2], 'hash', ARGV[3],
                'expires', ARGV[4], 'idle', ARGV[5], 'admin', ARGV[6], 'rotateAfter', '0')
            redis.call('PEXPIREAT', KEYS[1], ARGV[4])
            return 1
            """;
        redis.execute(new DefaultRedisScript<>(script, Long.class), List.of(key(userId)), sid, token,
            hash(token), Long.toString(expires), Long.toString(admin ? Math.min(expires, now + adminIdleTimeout) : expires), admin ? "1" : "0");
    }
    public String getSession(String userId) {
        Object value = redis.opsForHash().get(key(userId), "sid");
        return value == null ? null : value.toString();
    }
    public void removeSession(String userId) { redis.delete(key(userId)); }
    public void removeSession(String userId, String sid) {
        redis.execute(new DefaultRedisScript<>("if redis.call('HGET', KEYS[1], 'sid') == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0", Long.class), List.of(key(userId)), sid);
    }
    public boolean isValidSession(String userId, String sid) { return deadline(userId, sid, false) != null; }
    public Long deadline(String userId, String sid, boolean activity) {
        if (userId == null || sid == null) return null;
        String script = VALID + """
            if ARGV[3] == '1' and redis.call('HGET', KEYS[1], 'admin') == '1' then
                idle = math.min(expires, now + tonumber(ARGV[4]))
                redis.call('HSET', KEYS[1], 'idle', tostring(idle))
            end
            return math.min(expires, idle)
            """;
        return redis.execute(new DefaultRedisScript<>(script, Long.class), List.of(key(userId)), sid,
            Long.toString(System.currentTimeMillis()), activity ? "1" : "0", Long.toString(adminIdleTimeout));
    }
    public String rotate(String userId, String sid, String oldToken, String newToken) {
        // Concurrent tabs/retries receive the same successor during a 10-second overlap.
        // Reuse outside the overlap revokes the session. Refresh never extends its deadline.
        String script = VALID + """
            local current = redis.call('HGET', KEYS[1], 'hash')
            local after = tonumber(redis.call('HGET', KEYS[1], 'rotateAfter'))
            if ARGV[3] == current then
                if now < after then return redis.call('HGET', KEYS[1], 'token') end
                redis.call('HSET', KEYS[1], 'previous', current, 'rotateAfter', tostring(now + 10000),
                    'hash', ARGV[4], 'token', ARGV[5])
                return ARGV[5]
            end
            if now < after and ARGV[3] == redis.call('HGET', KEYS[1], 'previous') then
                return redis.call('HGET', KEYS[1], 'token')
            end
            redis.call('DEL', KEYS[1])
            return nil
            """;
        return redis.execute(new DefaultRedisScript<>(script, String.class), List.of(key(userId)), sid,
            Long.toString(System.currentTimeMillis()), hash(oldToken), hash(newToken), newToken);
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
