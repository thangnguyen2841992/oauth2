package com.thang.user.controller;

import com.thang.user.model.dto.*;
import com.thang.user.model.entity.User;
import com.thang.user.service.user.IUserService;
import com.thang.user.service.user.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final IUserService userService;
    private final SessionService sessionService;
    private final com.thang.user.service.user.JwtService jwtService;
    private final com.thang.user.service.user.GoogleOAuthState googleOAuthState;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.redirect-uri}")
    private String googleRedirectUri;
    @Value("${FRONTEND_URL:http://localhost:5173}") private String frontendUrl;
    @Value("${COOKIE_SECURE:false}") private boolean cookieSecure;


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {

            TokenUserResponse res = userService.login(request);

            ResponseCookie accessToken = getResponseCookie(res);
            ResponseCookie refreshToken = getCookie(res);

            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessToken.toString()).header(HttpHeaders.SET_COOKIE, refreshToken.toString()).body(Map.of("message", "Login success"));

        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Login storage unavailable: {}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Dịch vụ đăng nhập tạm thời gián đoạn. Vui lòng thử lại."));
        } catch (RuntimeException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    private @NonNull ResponseCookie getCookie(TokenUserResponse res) {
        return ResponseCookie.from("refreshToken", res.getRefresh_token()).httpOnly(true).path("/").maxAge(remaining(res.getRefreshExpiresAt())).sameSite("Lax").secure(cookieSecure).build();
    }

    private @NonNull ResponseCookie getResponseCookie(TokenUserResponse res) {
        return ResponseCookie.from("accessToken", res.getAccess_token()).httpOnly(true).path("/").maxAge(remaining(res.getAccessExpiresAt())).sameSite("Lax").secure(cookieSecure).build();
    }
    private long remaining(long expiresAt) { return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000); }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody CreateUserRequest request) {

        try {
            User saveUser = userService.createUser(request);

            return ResponseEntity.ok(Map.of("email", saveUser.getEmail(), "userId", saveUser.getUserId(), "message", "Đăng ký thành công. Vui lòng kiểm tra email để kích hoạt tài khoản."));

        } catch (Exception e) {
            return ResponseEntity.status(400).body("Đăng ký thất bại!");
        }
    }

    @GetMapping("/checkLogin")
    public ResponseEntity<?> checkLogin(@CookieValue(value = "accessToken", required = false) String token) {
        try {
            var claims = activeClaims(token, false);
            UserDTO user = userService.extractUsername(token);
            if (user == null) return ResponseEntity.status(401).body(Map.of("isLoggedIn", false));
            return ResponseEntity.ok(Map.of("isLoggedIn", true, "userId", user.getUserId(), "name", user.getFullName(),
                "email", user.getEmail(), "role", user.getRoleName(), "sessionId", claims.get("sessionId", String.class)));
        } catch (org.springframework.dao.DataAccessException e) {
            return ResponseEntity.status(503).body(Map.of("message", "Dịch vụ đăng nhập tạm thời gián đoạn"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("isLoggedIn", false));
        }
    }

    @PostMapping("/activity")
    public ResponseEntity<?> activity(@CookieValue(value = "accessToken", required = false) String token) {
        try {
            activeClaims(token, true);
            return ResponseEntity.noContent().build();
        } catch (org.springframework.dao.DataAccessException e) {
            return ResponseEntity.status(503).body(Map.of("message", "Dịch vụ đăng nhập tạm thời gián đoạn"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("message", "Phiên đăng nhập đã hết hạn"));
        }
    }

    private io.jsonwebtoken.Claims activeClaims(String token, boolean activity) {
        var claims = jwtService.parseAndValidate(token);
        if (!"access".equals(claims.get("type", String.class)) ||
            sessionService.deadline(claims.getSubject(), claims.get("sessionId", String.class), activity) == null) {
            throw new IllegalArgumentException("Session expired");
        }
        return claims;
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "refreshToken", required = false) String refreshToken,
                                   @CookieValue(value = "accessToken", required = false) String accessToken) {
        try {
            String token = refreshToken != null && !refreshToken.isBlank() ? refreshToken : accessToken;
            if (token != null && !token.isBlank()) {
                var claims = jwtService.parseAndValidate(token);
                String type = claims.get("type", String.class);
                if ("access".equals(type) || "refresh".equals(type))
                    sessionService.removeSession(claims.getSubject(), claims.get("sessionId", String.class));
            }
        } catch (org.springframework.dao.DataAccessException e) {
            return ResponseEntity.status(503).body(Map.of("message", "Chưa thể thu hồi phiên. Vui lòng thử đăng xuất lại."));
        } catch (RuntimeException e) { /* Already expired or invalid: clear browser cookies. */ }
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, ResponseCookie.from("accessToken", "").httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(0).build().toString())
            .header(HttpHeaders.SET_COOKIE, ResponseCookie.from("refreshToken", "").httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(0).build().toString())
            .body(Map.of("message", "logged out"));
    }
    @GetMapping("/checkEmail")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        String result = userService.checkEmailWhenLogin(email);

        return ResponseEntity.ok(Map.of("type", result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = "refreshToken", required = false) String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No refresh token"));
        }

        try {

            TokenUserResponse res = userService.refresh(refreshToken);

            ResponseCookie accessToken = getResponseCookie(res);

            ResponseCookie newRefreshToken = getCookie(res);

            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessToken.toString()).header(HttpHeaders.SET_COOKIE, newRefreshToken.toString()).body(Map.of("message", "refreshed"));

        } catch (Exception e) {

            if (e instanceof org.springframework.dao.DataAccessException) {
                log.warn("Refresh storage unavailable: {}", e.getClass().getSimpleName());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Dịch vụ đăng nhập tạm thời gián đoạn. Vui lòng thử lại."));
            }
            log.warn("Refresh token failed: {}", e.getMessage());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid or expired refresh token"));
        }
    }

    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response) throws IOException {
        String state = googleOAuthState.issue();
        response.addHeader(HttpHeaders.SET_COOKIE, googleStateCookie(state, 600).toString());
        String googleUrl = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleClientId).queryParam("redirect_uri", googleRedirectUri)
                .queryParam("response_type", "code").queryParam("scope", "openid email profile")
                .queryParam("prompt", "select_account").queryParam("state", state)
                .build().encode().toUriString();
        response.sendRedirect(googleUrl);
    }

    private ResponseCookie googleStateCookie(String state, long maxAge) {
        return ResponseCookie.from("googleOAuthState", state).httpOnly(true).secure(cookieSecure)
                .sameSite("Lax").path("/api/auth").maxAge(maxAge).build();
    }


    // =========================================================
    // GOOGLE CALLBACK
    // =========================================================

    @GetMapping("/callbackGoogle")
    public void callbackGoogle(@RequestParam String code,
                               @RequestParam(required = false) String state,
                               @CookieValue(value = "googleOAuthState", required = false) String expectedState,
                               HttpServletResponse response) throws IOException {
        response.addHeader(HttpHeaders.SET_COOKIE, googleStateCookie("", 0).toString());
        googleOAuthState.verify(state, expectedState);
        GoogleLoginResponse result = userService.loginWithGoogle(code);


        // =====================================================
        // USER ĐÃ CÓ ACCOUNT
        // =====================================================

        if ("LOGIN".equals(result.getStatus())) {

            setAuthCookies(response, result.getToken());

            response.sendRedirect(frontendUrl + "/");

            return;
        }


        // =====================================================
        // USER CHƯA CÓ ACCOUNT
        // =====================================================

        if ("SET_PASSWORD".equals(result.getStatus())) {

            String redirect = frontendUrl + "/google/setup-password" + "?token=" + result.getSetupToken() + "&email=" + java.net.URLEncoder.encode(result.getEmail(), java.nio.charset.StandardCharsets.UTF_8);

            response.sendRedirect(redirect);

            return;
        }


        throw new RuntimeException("Google login status không hợp lệ");
    }


    // =========================================================
    // GOOGLE SET PASSWORD
    // =========================================================

    @PostMapping("/google/setup-password")
    public void setupGooglePassword(@RequestBody GoogleSetupPasswordRequest request, HttpServletResponse response) throws IOException {

        TokenUserResponse token = userService.setupGooglePassword(request);

        setAuthCookies(response, token);

        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void setAuthCookies(HttpServletResponse response, TokenUserResponse token) {
        response.addHeader(HttpHeaders.SET_COOKIE, getResponseCookie(token).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, getCookie(token).toString());
    }
}
