package com.thang.user.service.user;

import com.thang.user.model.dto.identity.GetRoleIdResponse;
import com.thang.user.repository.IdentityClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RoleCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final IdentityClient identityClient;
    private final TokenCacheService tokenCacheService;

    private static final String ROLE_CACHE_KEY = "REALM_ROLES";

    /**
     * Lấy role từ cache hoặc Keycloak
     */
    public GetRoleIdResponse getRole(String roleName) {

        // 1. 🔥 Check Redis
        String roleId = (String) redisTemplate.opsForHash()
                .get(ROLE_CACHE_KEY, roleName);

        if (roleId != null) {
            return buildRole(roleId, roleName);
        }

        // 2. 🚀 Call Keycloak (realm role)
        String token = tokenCacheService.getClientToken();

        GetRoleIdResponse role = identityClient.getRealmRole(
                "Bearer " + token,
                roleName
        );

        // 3. 💾 Cache lại (chỉ lưu ID)
        redisTemplate.opsForHash()
                .put(ROLE_CACHE_KEY, roleName, role.getId());

        redisTemplate.expire(ROLE_CACHE_KEY, Duration.ofHours(24));

        return role;
    }

    /**
     * Build object từ cache
     */
    private GetRoleIdResponse buildRole(String id, String name) {
        GetRoleIdResponse role = new GetRoleIdResponse();
        role.setId(id);
        role.setName(name);
        return role;
    }

    /**
     * (Optional) Clear cache khi cần
     */
    public void evictAll() {
        redisTemplate.delete(ROLE_CACHE_KEY);
    }
}