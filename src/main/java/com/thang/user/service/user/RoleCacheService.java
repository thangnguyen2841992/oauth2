package com.thang.user.service.user;

import com.thang.user.repository.IdentityClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RoleCacheService {
    private final RedisTemplate<String, String> redisTemplate;
    private final IdentityClient identityClient;
    private final TokenCacheService tokenCacheService;
    private final ClientUuidCacheService clientUuidCacheService;
    @Value("${spring.idp.client-id}")
    private String clientId;

    private static final String ROLE_HASH_KEY = "KEYCLOAK_ROLES";

    public void getRoleId(String roleName) {

        // 🔥 1. Check Redis (HASH)
        Object roleId = redisTemplate.opsForHash().get(ROLE_HASH_KEY, roleName);

        if (roleId != null) {
            System.out.println("🔥 ROLE from Redis: " + roleName);
            roleId.toString();
            return;
        }

        // 🔥 2. Call API
        System.out.println("🚀 CALL API lấy ROLE: " + roleName);

        String token = tokenCacheService.getClientToken();

        String clientUUID = clientUuidCacheService
                .getAllClientUuid()
                .get(clientId);

        if (clientUUID == null) {
            throw new RuntimeException("Client UUID not found");
        }

        var role = identityClient.getRoleId(
                "Bearer " + token,
                clientUUID,
                roleName
        );

        String id = role.getId();

        // 🔥 3. Cache vào HASH
        redisTemplate.opsForHash().put(ROLE_HASH_KEY, roleName, id);
        redisTemplate.expire(ROLE_HASH_KEY, Duration.ofHours(24));

    }
}
