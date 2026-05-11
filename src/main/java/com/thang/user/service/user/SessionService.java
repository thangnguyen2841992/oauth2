package com.thang.user.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String SESSION_KEY_PREFIX =
            "user:session:";

    // session timeout
    private static final long SESSION_TTL_HOURS = 24;

    /**
     * save current session
     */
    public void saveSession(
            String userId,
            String sessionId
    ) {

        redisTemplate.opsForValue().set(

                buildKey(userId),

                sessionId,

                Duration.ofHours(
                        SESSION_TTL_HOURS
                )
        );
    }

    /**
     * get current session
     */
    public String getSession(
            String userId
    ) {

        return redisTemplate.opsForValue()
                .get(buildKey(userId));
    }

    /**
     * remove session
     */
    public void removeSession(
            String userId
    ) {

        redisTemplate.delete(
                buildKey(userId)
        );
    }

    /**
     * check session valid
     */
    public boolean isValidSession(
            String userId,
            String sessionId
    ) {

        String currentSession =
                getSession(userId);

        return currentSession != null &&
                currentSession.equals(sessionId);
    }

    /**
     * refresh ttl
     */
    public void refreshSession(
            String userId
    ) {

        redisTemplate.expire(

                buildKey(userId),

                Duration.ofHours(
                        SESSION_TTL_HOURS
                )
        );
    }

    /**
     * redis key
     */
    private String buildKey(
            String userId
    ) {

        return SESSION_KEY_PREFIX + userId;
    }
}