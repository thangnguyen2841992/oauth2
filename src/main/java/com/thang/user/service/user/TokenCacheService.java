package com.thang.user.service.user;


import com.thang.user.model.dto.identity.TokenExchangeParam;
import com.thang.user.repository.IdentityClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final IdentityClient identityClient;
    private final RedissonClient redissonClient;

    @Value("${spring.idp.client-id}")
    private String clientId;

    @Value("${spring.idp.client-secret}")
    private String clientSecret;

    private static final String TOKEN_KEY_PREFIX = "keycloak:client_token:";
    private static final long BUFFER_SECONDS = 60;

    public String getClientToken() {

        String key = buildKey();

        String token = redisTemplate.opsForValue().get(key);
        if (token != null) {
            return token;
        }

        RLock lock = redissonClient.getLock("lock:" + key);

        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {

                token = redisTemplate.opsForValue().get(key);
                if (token != null) {
                    return token;
                }

                log.info("Fetching new token from Keycloak for client={}", clientId);

                var response = identityClient.exchangeClientToken(
                        TokenExchangeParam.builder()
                                .grant_type("client_credentials")
                                .client_id(clientId)
                                .client_secret(clientSecret)
                                .scope("openid")
                                .build()
                );

                String accessToken = response.getAccess_token();
                long expiresIn = response.getExpires_in();

                long ttl = Math.max(expiresIn - BUFFER_SECONDS, 30);

                redisTemplate.opsForValue().set(
                        key,
                        accessToken,
                        Duration.ofSeconds(ttl)
                );

                return accessToken;
            }

        } catch (Exception e) {
            log.error("Error getting token", e);
            throw new RuntimeException("Cannot get token from Keycloak", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        throw new RuntimeException("Cannot acquire lock for token");
    }

    public void evictToken() {
        redisTemplate.delete(buildKey());
    }

    private String buildKey() {
        return TOKEN_KEY_PREFIX + clientId;
    }
}