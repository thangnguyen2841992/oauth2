package com.thang.user.service.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Bind the Google callback to the browser that initiated login. */
@Component
public class GoogleOAuthState {
    private final SecureRandom random = new SecureRandom();

    public String issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void verify(String returned, String expected) {
        if (returned == null || expected == null || returned.length() != 43 || expected.length() != 43
                || !MessageDigest.isEqual(returned.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phiên đăng nhập Google không hợp lệ. Vui lòng bắt đầu đăng nhập lại.");
        }
    }
}
