package com.thang.user.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_KEY_PREFIX = "user:session:";

    private static final long SESSION_TTL_HOURS = 24;

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Lưu session hiện tại của user.
     * <p>
     * Mỗi user chỉ có 1 session.
     * Login lần mới sẽ ghi đè session cũ.
     */
    public void saveSession(String userId, String sessionId) {

        redisTemplate.opsForValue().set(buildKey(userId), sessionId, Duration.ofHours(SESSION_TTL_HOURS));
    }

    /**
     * Lấy session hiện tại của user.
     */
    public String getSession(String userId) {

        return redisTemplate.opsForValue().get(buildKey(userId));
    }

    /**
     * Xóa session của user.
     */
    public void removeSession(String userId) {

        redisTemplate.delete(buildKey(userId));
    }

    /**
     * Kiểm tra session có còn hợp lệ hay không.
     */
    public boolean isValidSession(String userId, String sessionId) {

        if (userId == null || sessionId == null) {
            return false;
        }

        String currentSession = getSession(userId);

        return sessionId.equals(currentSession);
    }

    /**
     * Gia hạn TTL session.
     */
    public void refreshSession(String userId) {

        redisTemplate.expire(buildKey(userId), Duration.ofHours(SESSION_TTL_HOURS));
    }

    /**
     * Redis key.
     */
    private String buildKey(String userId) {

        return SESSION_KEY_PREFIX + userId;
    }
}
