package com.thang.user.service.user;

import com.thang.user.model.dto.TokenUserResponse;
import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtService jwtService;
    private final SessionService sessionService;
    private final IUserRepository userRepository;

    public TokenUserResponse generateToken(
            User user,
            String sessionId
    ) {

        String accessToken =
                jwtService.generateAccessToken(
                        user,
                        sessionId
                );

        String refreshToken =
                jwtService.generateRefreshToken(
                        user,
                        sessionId
                );

        return TokenUserResponse.builder()
                .access_token(accessToken)
                .refresh_token(refreshToken)
                .build();
    }

    public TokenUserResponse refreshToken(String refreshToken) {

        // 1. Kiểm tra refresh token
        var claims = jwtService.parseAndValidate(refreshToken);

        // 2. Phải là refresh token
        String type = claims.get("type", String.class);

        if (!"refresh".equals(type)) {
            throw new RuntimeException("Invalid refresh token");
        }

        // 3. Lấy userId
        String userId = claims.getSubject();

        if (userId == null || userId.isBlank()) {
            throw new RuntimeException("Invalid refresh token");
        }

        // 4. Lấy sessionId
        String sessionId =
                claims.get("sessionId", String.class);

        if (sessionId == null || sessionId.isBlank()) {
            throw new RuntimeException("Invalid session");
        }

        // 5. Kiểm tra session Redis
        boolean validSession =
                sessionService.isValidSession(
                        userId,
                        sessionId
                );

        if (!validSession) {
            throw new RuntimeException(
                    "Session expired or logged out"
            );
        }

        // 6. Lấy user
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        // 7. Kiểm tra user còn active
        if (!user.isActive()) {
            throw new RuntimeException("User is inactive");
        }

        // 8. Tạo access token mới
        String newAccessToken =
                jwtService.generateAccessToken(
                        user,
                        sessionId
                );

        return TokenUserResponse.builder()
                .access_token(newAccessToken)
                .refresh_token(refreshToken)
                .build();
    }
}