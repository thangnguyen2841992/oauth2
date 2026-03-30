package com.thang.user.service.user;


import com.thang.user.model.dto.identity.TokenExchangeParam;
import com.thang.user.repository.IdentityClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final IdentityClient identityClient;

    @Value("${spring.idp.client-id}")
    private String clientId;

    @Value("${spring.idp.client-secret}")
    private String clientSecret;

    private static final String TOKEN_KEY = "KEYCLOAK_CLIENT_TOKEN";

    public String getClientToken() {

        // 🔥 1. Check Redis
        String token = redisTemplate.opsForValue().get(TOKEN_KEY);
        if (token != null) {
            return token;
        }

        // 🔥 2. Lock tránh nhiều thread cùng gọi Keycloak
        synchronized (this) {

            // double-check
            token = redisTemplate.opsForValue().get(TOKEN_KEY);
            if (token != null) {
                return token;
            }

            // 🔥 3. Call Keycloak
            var response = identityClient.exchangeClientToken(
                    TokenExchangeParam.builder()
                            .grant_type("client_credentials")
                            .client_id(clientId)
                            .client_secret(clientSecret)
                            .scope("openid")
                            .build()
            );

            String accessToken = response.getAccess_token();

            // 🔥 4. TTL theo expires_in (chuẩn hơn)
            long expiresIn = response.getExpires_in(); // seconds

            redisTemplate.opsForValue().set(
                    TOKEN_KEY,
                    accessToken,
                    java.time.Duration.ofSeconds(expiresIn - 60) // buffer 60s
            );

            return accessToken;
        }
    }
}