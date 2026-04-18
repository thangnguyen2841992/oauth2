package com.thang.user.controller;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.dto.identity.TokenUserResponse;
import com.thang.user.model.entity.User;
import com.thang.user.service.user.IUserService;
import com.thang.user.utils.CookieUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IUserService userService;

    public AuthController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            TokenUserResponse res = userService.login(request);

            ResponseCookie accessToken = ResponseCookie.from("accessToken", res.getAccess_token())
                    .httpOnly(true)
                    .path("/")
                    .maxAge(60 * 60)
                    .sameSite("Lax")
                    .secure(false)
                    .build();

            ResponseCookie refreshToken = ResponseCookie.from("refreshToken", res.getRefresh_token())
                    .httpOnly(true)
                    .path("/")
                    .maxAge(60 * 60)
                    .sameSite("Lax")
                    .secure(false)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessToken.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshToken.toString())
                    .body(Map.of(
                            "message", "Login success"
                    ));

        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                    "message", "Sai tài khoản hoặc mật khẩu"
            ));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody CreateUserRequest request) {

        try {
            User saveUser = userService.createUser(request);

            return ResponseEntity.ok(
                    Map.of(
                            "userId", saveUser.getId(),
                            "email", saveUser.getEmail()
                    )
            );

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

                return ResponseEntity.ok(
                        Map.of(
                                "isLoggedIn", true,
                                "name", userDTO.getFullName(),
                                "role", userDTO.getRoleName()
                        )
                );
            } catch (Exception e) {
                return ResponseEntity.ok(
                        Map.of("isLoggedIn", false)
                );
            }
        }
        return ResponseEntity.ok(
                Map.of("isLoggedIn", false)
        );
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request,
                                    HttpServletResponse response) {

        String refreshToken = CookieUtils.get(request, "refreshToken");

        if (refreshToken != null) {
            userService.logout(refreshToken);
        }

        CookieUtils.clear(response, "accessToken");
        CookieUtils.clear(response, "refreshToken");

        return ResponseEntity.ok(Map.of(
                "message", "Logout success"
        ));
    }

    @GetMapping("/callbackGoogle")
    public void callback(@RequestParam String code,
                         HttpServletResponse response) throws IOException {

        TokenUserResponse token = userService.handleOAuth2Login(code);

        String accessToken = token.getAccess_token();

        Cookie cookie = new Cookie("accessToken", accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(3600);

        response.addCookie(cookie);

        response.sendRedirect("http://localhost:5173/");
    }

    @GetMapping("/checkEmail")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        String result = userService.checkEmailWhenLogin(email);

        return ResponseEntity.ok(Map.of(
                "type", result
        ));
    }

}
