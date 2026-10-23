package com.thang.user.controller;

import com.thang.user.model.dto.TokenUserResponse;
import org.springframework.http.ResponseCookie;
import java.util.List;

/**
 * Builds the authentication cookies in one place. The access token goes to every API ({@code Path=/}); the refresh
 * token is only needed by /api/auth/refresh and /api/auth/logout, so it is scoped to {@code refreshPath} and no
 * longer travels with every request. Older deployments set the refresh cookie on "/", so each issue/clear also
 * expires that copy; otherwise browsers would keep sending two refresh cookies.
 */
final class AuthCookies {
    static final String DEFAULT_REFRESH_PATH = "/api/auth";
    private AuthCookies() {}

    static List<String> issue(TokenUserResponse tokens, boolean secure, String refreshPath) {
        return withLegacyClear(secure, refreshPath,
                cookie("accessToken", tokens.getAccess_token(), "/", remaining(tokens.getAccessExpiresAt()), secure),
                cookie("refreshToken", tokens.getRefresh_token(), refreshPath, remaining(tokens.getRefreshExpiresAt()), secure));
    }

    static List<String> clear(boolean secure, String refreshPath) {
        return withLegacyClear(secure, refreshPath,
                cookie("accessToken", "", "/", 0, secure),
                cookie("refreshToken", "", refreshPath, 0, secure));
    }

    private static List<String> withLegacyClear(boolean secure, String refreshPath, String access, String refresh) {
        if ("/".equals(refreshPath)) return List.of(access, refresh);
        return List.of(access, refresh, cookie("refreshToken", "", "/", 0, secure));
    }

    private static String cookie(String name, String value, String path, long maxAge, boolean secure) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secure).sameSite("Lax").path(path).maxAge(maxAge).build().toString();
    }

    private static long remaining(long expiresAt) { return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000); }
}
