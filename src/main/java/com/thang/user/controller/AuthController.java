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
    private final com.nihongo.security.InternalCredentials internalCredentials;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.redirect-uri}")
    private String googleRedirectUri;
    @Value("${FRONTEND_URL:http://localhost:5173}") private String frontendUrl;
    @Value("${COOKIE_SECURE:true}") private boolean cookieSecure = true;
    @Value("${auth.refresh-cookie-path:" + AuthCookies.DEFAULT_REFRESH_PATH + "}") private String refreshCookiePath = AuthCookies.DEFAULT_REFRESH_PATH;


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {

            TokenUserResponse res = userService.login(request);

            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authCookies(res)).body(Map.of("message", "Login success"));

        } catch (com.thang.user.service.user.MfaRequiredException required) {
            return ResponseEntity.status(202).header(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("mfaChallenge", required.challenge()).path("/").maxAge(300).httpOnly(true).secure(cookieSecure).sameSite("Lax").build().toString())
                    .body(Map.of("mfaRequired", true));
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Login storage unavailable: {}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Dịch vụ đăng nhập tạm thời gián đoạn. Vui lòng thử lại."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Tài khoản hoặc mật khẩu không đúng"));
        }
    }

    private String[] authCookies(TokenUserResponse res) {
        return AuthCookies.issue(res, cookieSecure, refreshCookiePath).toArray(String[]::new);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody CreateUserRequest request) {

        try {
            User saveUser = userService.createUser(request);

            return ResponseEntity.ok(Map.of("email", saveUser.getEmail(), "userId", saveUser.getUserId(), "message", "Đăng ký thành công. Vui lòng kiểm tra email để kích hoạt tài khoản."));

        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Đăng ký thất bại" : e.getReason()));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email đã tồn tại"));
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Registration storage unavailable: {}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "Dịch vụ đăng ký tạm thời gián đoạn. Vui lòng thử lại."));
        } catch (RuntimeException e) {
            log.error("Registration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Đăng ký thất bại. Vui lòng thử lại."));
        }
    }

    @GetMapping("/checkLogin")
    public ResponseEntity<?> checkLogin(@CookieValue(value = "accessToken", required = false) String token) {
        try {
            var claims = activeClaims(token, false);
            UserDTO user = userService.extractUsername(claims);
            if (user == null) return ResponseEntity.status(401).body(Map.of("isLoggedIn", false));
            return ResponseEntity.ok(Map.of("isLoggedIn", true, "userId", user.getUserId(), "name", user.getFullName(),
                "email", user.getEmail(), "role", user.getRoleName(), "sessionId", claims.get("sessionId", String.class),
                "accessExpiresAt", claims.getExpiration().getTime()));
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
        if (!activity && "access".equals(claims.get("type", String.class)) && sessionService.confirmedDeadline(claims.getSubject(),
                claims.get("sessionId", String.class), claims.get("roles", java.util.List.class)) != null) return claims;
        if (!"access".equals(claims.get("type", String.class)) ||
            sessionService.accessDeadline(claims.getSubject(),claims.get("sessionId",String.class),claims.get("roles",java.util.List.class),activity)==null) {
            throw new IllegalArgumentException("Session expired");
        }
        return claims;
    }

    // Called directly by the gateway. A valid signed access token is required.
    @GetMapping("/session/validate")
    public ResponseEntity<Void> validateSession(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value=com.nihongo.security.InternalCredentials.HEADER,required=false) String internalKey) {
        if(!internalCredentials.matches(internalKey))return ResponseEntity.status(401).build();
        if (authorization == null || !authorization.startsWith("Bearer ")) return ResponseEntity.status(401).build();
        try {
            activeClaims(authorization.substring(7), false);
            return ResponseEntity.noContent().build();
        } catch (org.springframework.dao.DataAccessException e) {
            return ResponseEntity.status(503).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).build();
        }
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
            .header(HttpHeaders.SET_COOKIE, AuthCookies.clear(cookieSecure, refreshCookiePath).toArray(String[]::new))
            .body(Map.of("message", "logged out"));
    }
    @GetMapping("/checkEmail")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        if (email == null || email.length() > 255 || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email không hợp lệ"));
        }
        return ResponseEntity.ok(Map.of("type", "LOCAL"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = "refreshToken", required = false) String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No refresh token"));
        }

        try {

            TokenUserResponse res = userService.refresh(refreshToken);

            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authCookies(res))
                    .body(Map.of("message", "refreshed", "accessExpiresAt", res.getAccessExpiresAt()));

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
        GoogleLoginResponse result;
        try { result = userService.loginWithGoogle(code); }
        catch (com.thang.user.service.user.MfaRequiredException required) {
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("mfaChallenge", required.challenge()).path("/").maxAge(300).httpOnly(true).secure(cookieSecure).sameSite("Lax").build().toString());
            response.sendRedirect(frontendUrl + "/login?mfa=1");
            return;
        }


        // =====================================================
        // USER ĐÃ CÓ ACCOUNT
        // =====================================================

        if ("LOGIN".equals(result.getStatus())) {

            setAuthCookies(response, result.getToken());

            var roles = jwtService.parseAndValidate(result.getToken().getAccess_token()).get("roles", java.util.List.class);
            String destination = roles != null && roles.contains("USER") ? "/user/my-courses" : "/";
            response.sendRedirect(frontendUrl + destination);

            return;
        }


        // =====================================================
        // USER CHƯA CÓ ACCOUNT
        // =====================================================

        if ("SET_PASSWORD".equals(result.getStatus())) {

            String redirect = frontendUrl + "/google/setup-password" + "#token="
                    + java.net.URLEncoder.encode(result.getSetupToken(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&email=" + java.net.URLEncoder.encode(result.getEmail(), java.nio.charset.StandardCharsets.UTF_8);

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
        for (String cookie : authCookies(token)) response.addHeader(HttpHeaders.SET_COOKIE, cookie);
    }
}
