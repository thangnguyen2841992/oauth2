package com.thang.user;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RevokedTokenService {
    private final StringRedisTemplate redisTemplate;
    // prefix key
    private static final String KEY_PREFIX = "revoked_jti:";

    public RevokedTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    /**
     * Revoke a token id for given TTL.
     * @param jti token id
     * @param ttl duration to keep the revocation entry
     */
    public void revoke(String jti, Duration ttl) {
        if (jti == null || jti.isBlank()) return;
        String key = KEY_PREFIX + jti;
        // set a simple value and TTL
        redisTemplate.opsForValue().set(key, "1", ttl.getSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Check whether jti is revoked.
     * @param jti token id
     * @return true if an entry exists in redis (not expired)
     */
    public boolean isRevoked(String jti) {
        if (jti == null) return false;
        String key = KEY_PREFIX + jti;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
}
