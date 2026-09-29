package com.thang.user.service.user;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class PasswordPolicy {
    private static final Pattern COMPLEXITY = Pattern.compile("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9]).*");
    private PasswordPolicy() {}

    public static boolean valid(String password) {
        return password != null && password.length() >= 8
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && COMPLEXITY.matcher(password).matches();
    }
}
