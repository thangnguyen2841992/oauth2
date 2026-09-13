package com.thang.user.service.user;

import com.thang.user.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration
    ) {

        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );

        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String generateAccessToken(
            User user,
            String sessionId
    ) {

        return Jwts.builder()
                .subject(user.getUserId())
                .claim("email", user.getEmail())
                .claim(
                        "name",
                        user.getFirstName() + " " + user.getLastName()
                )
                .claim(
                        "roles",
                        List.of(user.getRoleName())
                )
                .claim("sessionId", sessionId)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + accessExpiration
                        )
                )
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(
            User user,
            String sessionId
    ) {

        return Jwts.builder()
                .subject(user.getUserId())
                .claim("sessionId", sessionId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + refreshExpiration
                        )
                )
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAndValidate(String token) {

        if (token == null || token.isBlank()) {
            throw new RuntimeException(
                    "JWT không được để trống"
            );
        }

        try {

            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (Exception e) {

            throw new RuntimeException(
                    "JWT không hợp lệ hoặc đã hết hạn",
                    e
            );
        }
    }
}