package com.thang.user.service.user;

import com.thang.user.model.dto.identity.GetUuidClientResponse;
import com.thang.user.repository.IdentityClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientUuidCacheService {
    private final RedisTemplate<String, String> redisTemplate;
    private final IdentityClient identityClient;
    private final TokenCacheService tokenCacheService;

    @Value("${spring.idp.client-id}")
    private String clientId;

    private static final String ALL_CLIENTS_KEY = "KEYCLOAK_ALL_CLIENTS";

    public Map<String, String> getAllClientUuid() {

        Map<Object, Object> cache = redisTemplate.opsForHash().entries(ALL_CLIENTS_KEY);

        if (!cache.isEmpty()) {
            return cache.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> (String) e.getKey(),
                            e -> (String) e.getValue()
                    ));
        }

        var token = tokenCacheService.getClientToken();
        var response = identityClient.getUuidClient("Bearer " + token);

        Map<String, String> map = response.stream()
                .collect(Collectors.toMap(
                        GetUuidClientResponse::getClientId,
                        GetUuidClientResponse::getId
                ));

        redisTemplate.opsForHash().putAll(ALL_CLIENTS_KEY, map);
        redisTemplate.expire(ALL_CLIENTS_KEY, Duration.ofHours(24));

        return map;
    }
}
