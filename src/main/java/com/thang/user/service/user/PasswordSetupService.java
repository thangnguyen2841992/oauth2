package com.thang.user.service.user;
import com.thang.user.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class PasswordSetupService {
    private final IUserRepository users;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    @Transactional
    public Map<String,String> activate(String userId, String code) {
        var user = users.lockById(userId).orElseThrow(() -> invalid());
        if (user.isActive()) return Map.of("status", "ALREADY_ACTIVE");
        if (code == null || user.getCodeActive() == null || !MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8), user.getCodeActive().getBytes(StandardCharsets.UTF_8))) throw invalid();
        if (user.getCodeActiveExpiredAt() == null || !user.getCodeActiveExpiredAt().isAfter(LocalDateTime.now()))
            return Map.of("status", "EXPIRED", "userId", userId);
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        user.setActive(true); user.setCodeActive(null); user.setCodeActiveExpiredAt(null);
        user.setPasswordSetupHash(hash(token)); user.setPasswordSetupExpiresAt(LocalDateTime.now().plusMinutes(15));
        users.save(user);
        return Map.of("status", "SUCCESS", "email", user.getEmail(), "userId", userId, "setupToken", token);
    }
    @Transactional
    public void reset(SetupRequest request) {
        if (request == null || request.userId() == null || request.token() == null || request.token().length() > 100) throw invalid();
        String password = request.password();
        if (!PasswordPolicy.valid(password)
            || !password.equals(request.confirmPassword())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu cần 8–72 byte, chữ hoa, chữ thường, số, ký tự đặc biệt và xác nhận trùng khớp");
        var user = users.lockById(request.userId()).orElseThrow(() -> invalid());
        if (user.getPasswordSetupHash() == null || user.getPasswordSetupExpiresAt() == null
            || !user.getPasswordSetupExpiresAt().isAfter(LocalDateTime.now())
            || !MessageDigest.isEqual(user.getPasswordSetupHash().getBytes(StandardCharsets.UTF_8),hash(request.token()).getBytes(StandardCharsets.UTF_8))) throw invalid();
        user.setPassword(encoder.encode(password)); user.setPasswordSetupHash(null); user.setPasswordSetupExpiresAt(null);
        user.setDateModified(LocalDateTime.now()); users.save(user);
        sessions.removeSession(user.getUserId());
    }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Liên kết xác minh không hợp lệ hoặc đã hết hạn"); }
    public record SetupRequest(String userId, String token, String password, String confirmPassword) {}
}
