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

    public TokenUserResponse generateToken(User user, String sid) {
        long expires = (System.currentTimeMillis() + jwtService.sessionDuration(user)) / 1000 * 1000;
        String refresh = jwtService.generateRefreshToken(user, sid, expires);
        sessionService.create(user.getUserId(), sid, refresh, expires, "ADMIN".equals(user.getRoleName()));
        return response(user, sid, refresh, expires);
    }
    public TokenUserResponse refreshToken(String token) {
        var claims = jwtService.parseAndValidate(token);
        if (!"refresh".equals(claims.get("type", String.class))) throw new IllegalArgumentException("Invalid refresh token");
        String userId = claims.getSubject();
        String sid = claims.get("sessionId", String.class);
        if (userId == null || sid == null || !sessionService.isValidSession(userId, sid)) throw new IllegalArgumentException("Session expired or logged out");
        User user = userRepository.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isActive()) throw new IllegalArgumentException("User is inactive");
        long expires = claims.getExpiration().getTime();
        String next = jwtService.generateRefreshToken(user, sid, expires);
        String rotated = sessionService.rotate(userId, sid, token, next);
        if (rotated == null) throw new IllegalArgumentException("Refresh token reused or session expired");
        return response(user, sid, rotated, expires);
    }
    private TokenUserResponse response(User user, String sid, String refresh, long expires) {
        Long deadline = sessionService.deadline(user.getUserId(), sid, false);
        if (deadline == null) throw new IllegalArgumentException("Session expired");
        String access = jwtService.generateAccessToken(user, sid, Math.min(deadline, expires));
        return TokenUserResponse.builder().access_token(access).refresh_token(refresh)
            .accessExpiresAt(jwtService.parseAndValidate(access).getExpiration().getTime())
            .refreshExpiresAt(expires).build();
    }
}
