package com.thang.user.controller;

import com.thang.user.model.dto.*;
import com.thang.user.model.entity.User;
import com.thang.user.service.user.IUserService;
import com.thang.user.service.user.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.redirect-uri}")
    private String googleRedirectUri;


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {

            TokenUserResponse res = userService.login(request);

            ResponseCookie accessToken = getResponseCookie(res);
            ResponseCookie refreshToken = getCookie(res);

            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessToken.toString()).header(HttpHeaders.SET_COOKIE, refreshToken.toString()).body(Map.of("message", "Login success"));

        } catch (RuntimeException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    private static @NonNull ResponseCookie getCookie(TokenUserResponse res) {
        return ResponseCookie.from("refreshToken", res.getRefresh_token()).httpOnly(true).path("/").maxAge(30 * 60).sameSite("Lax").secure(false).build();
    }

    private static @NonNull ResponseCookie getResponseCookie(TokenUserResponse res) {
        return ResponseCookie.from("accessToken", res.getAccess_token()).httpOnly(true).path("/").maxAge(5 * 60).sameSite("Lax").secure(false).build();
    }

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
    public ResponseEntity<?> checkLogin(HttpServletRequest request) {
        String token = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    token = cookie.getValue();
                }
            }
        }
        if (token != null) {
            try {
                UserDTO userDTO = userService.extractUsername(token);

                return ResponseEntity.ok(Map.of("isLoggedIn", true, "name", userDTO.getFullName(), "email", userDTO.getEmail(), "role", userDTO.getRoleName()));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("isLoggedIn", false));
            }
        }
        return ResponseEntity.ok(Map.of("isLoggedIn", false));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "accessToken", required = false) String accessToken) {

        if (accessToken != null && !accessToken.isBlank()) {

            try {

                UserDTO user = userService.extractUsername(accessToken);

                if (user != null) {

                    User userEntity = userService.findUserByEmail(user.getEmail());

                    if (userEntity != null) {

                        String userId = userEntity.getUserId();

                        // Xóa session Redis
                        sessionService.removeSession(userId);
                    }
                }

            } catch (Exception e) {

                log.warn("Logout failed: {}", e.getMessage());
            }
        }

        // Xóa access token
        ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", "").httpOnly(true).path("/").maxAge(0).build();

        // Xóa refresh token
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", "").httpOnly(true).path("/").maxAge(0).build();

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString()).header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString()).body(Map.of("message", "logged out"));
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

            log.warn("Refresh token failed: {}", e.getMessage());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid or expired refresh token"));
        }
    }

    @GetMapping("/google")
    public void googleLogin(
            HttpServletResponse response
    ) throws IOException {

        String googleUrl =
                "https://accounts.google.com/o/oauth2/v2/auth"
                        + "?client_id=" + googleClientId
                        + "&redirect_uri=" + googleRedirectUri
                        + "&response_type=code"
                        + "&scope=openid%20email%20profile"
                        + "&prompt=select_account";

        response.sendRedirect(googleUrl);
    }


    // =========================================================
    // GOOGLE CALLBACK
    // =========================================================

    @GetMapping("/callbackGoogle")
    public void callbackGoogle(
            @RequestParam String code,
            HttpServletResponse response
    ) throws IOException {

        GoogleLoginResponse result =
                userService.loginWithGoogle(code);


        // =====================================================
        // USER ĐÃ CÓ ACCOUNT
        // =====================================================

        if ("LOGIN".equals(result.getStatus())) {

            setAuthCookies(
                    response,
                    result.getToken()
            );

            response.sendRedirect(
                    "http://localhost:5173/"
            );

            return;
        }


        // =====================================================
        // USER CHƯA CÓ ACCOUNT
        // =====================================================

        if ("SET_PASSWORD".equals(
                result.getStatus()
        )) {

            String redirect =
                    "http://localhost:5173/google/setup-password"
                            + "?token="
                            + result.getSetupToken()
                            + "&email="
                            + java.net.URLEncoder.encode(
                            result.getEmail(),
                            java.nio.charset.StandardCharsets.UTF_8
                    );

            response.sendRedirect(
                    redirect
            );

            return;
        }


        throw new RuntimeException(
                "Google login status không hợp lệ"
        );
    }


    // =========================================================
    // GOOGLE SET PASSWORD
    // =========================================================

    @PostMapping("/google/setup-password")
    public void setupGooglePassword(
            @RequestBody GoogleSetupPasswordRequest request,
            HttpServletResponse response
    ) throws IOException {

        TokenUserResponse token =
                userService.setupGooglePassword(
                        request
                );

        setAuthCookies(
                response,
                token
        );

        response.setStatus(
                HttpServletResponse.SC_OK
        );
    }

    private void setAuthCookies(
            HttpServletResponse response,
            TokenUserResponse token
    ) {

        Cookie accessCookie =
                new Cookie(
                        "accessToken",
                        token.getAccess_token()
                );

        accessCookie.setHttpOnly(true);

        accessCookie.setSecure(false);

        accessCookie.setPath("/");

        accessCookie.setMaxAge(300);

        response.addCookie(
                accessCookie
        );


        // =====================================================
        // REFRESH TOKEN
        // =====================================================

        if (token.getRefresh_token() != null &&
                !token.getRefresh_token().isBlank()) {

            Cookie refreshCookie =
                    new Cookie(
                            "refreshToken",
                            token.getRefresh_token()
                    );

            refreshCookie.setHttpOnly(true);

            refreshCookie.setSecure(false);

            refreshCookie.setPath("/");

            refreshCookie.setMaxAge(
                    7 * 24 * 60 * 60
            );

            response.addCookie(
                    refreshCookie
            );
        }
    }
}
