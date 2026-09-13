package com.thang.user.service.user;

import com.thang.user.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.access-expiration}") long accessExpiration, @Value("${jwt.refresh-expiration}") long refreshExpiration) {

        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    /**
     * Generate Access Token.
     */
    public String generateAccessToken(User user, String sessionId) {

        return Jwts.builder().subject(user.getUserId())

                .claim("email", user.getEmail())

                .claim("name", user.getFirstName() + " " + user.getLastName())

                .claim("roles", List.of(user.getRoleName()))

                .claim("sessionId", sessionId)

                .claim("type", "access")

                .issuedAt(new Date())

                .expiration(new Date(System.currentTimeMillis() + accessExpiration))

                .signWith(secretKey, Jwts.SIG.HS256)

                .compact();
    }

    /**
     * Generate Refresh Token.
     */
    public String generateRefreshToken(User user, String sessionId) {

        return Jwts.builder().subject(user.getUserId())

                .claim("sessionId", sessionId)

                .claim("type", "refresh")

                .issuedAt(new Date())

                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))

                .signWith(secretKey, Jwts.SIG.HS256)

                .compact();
    }

    /**
     * Parse và validate JWT.
     * <p>
     * Kiểm tra:
     * - JWT có hợp lệ không
     * - Signature
     * - Expiration
     */
    public Claims parseAndValidate(String token) {

        if (token == null || token.isBlank()) {

            throw new RuntimeException("JWT không được để trống");
        }

        try {

            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();

        } catch (Exception e) {

            throw new RuntimeException("JWT không hợp lệ hoặc đã hết hạn", e);
        }
    }

    /**
     * Lấy userId từ JWT.
     */
    public String getUserId(String token) {

        Claims claims = parseAndValidate(token);

        return claims.getSubject();
    }

    /**
     * Lấy sessionId từ JWT.
     */
    public String getSessionId(String token) {

        Claims claims = parseAndValidate(token);

        return claims.get("sessionId", String.class);
    }

    /**
     * Lấy type của JWT.
     * <p>
     * access / refresh
     */
    public String getTokenType(String token) {

        Claims claims = parseAndValidate(token);

        return claims.get("type", String.class);
    }

    public String generateGoogleSetupToken(
            String email,
            String googleId,
            String firstName,
            String lastName
    ) {

        return Jwts.builder()

                .claim("email", email)

                .claim("googleId", googleId)

                .claim("firstName", firstName)

                .claim("lastName", lastName)

                .claim("type", "google_setup")

                .issuedAt(new Date())

                // setup token chỉ sống 10 phút
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + 10 * 60 * 1000
                        )
                )

                .signWith(
                        secretKey,
                        Jwts.SIG.HS256
                )

                .compact();
    }
    public Claims parseGoogleSetupToken(
            String token
    ) {

        Claims claims = parseAndValidate(token);

        String type =
                claims.get("type", String.class);

        if (!"google_setup".equals(type)) {

            throw new RuntimeException(
                    "Invalid Google setup token"
            );
        }

        return claims;
    }
}
