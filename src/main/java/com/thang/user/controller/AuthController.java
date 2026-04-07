package com.thang.user.controller;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.identity.TokenUserResponse;
import com.thang.user.model.entity.User;
import com.thang.user.service.user.IUserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

@Controller
@RequestMapping("/api/auth")
public class AuthController {

    private final IUserService userService;

    public AuthController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpServletResponse response,
                        Model model) {

        try {
            LoginRequest request = new LoginRequest();
            request.setUsername(username);
            request.setPassword(password);

            TokenUserResponse res = userService.login(request);

            String token = res.getAccess_token();

            Cookie cookie = new Cookie("accessToken", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60);

            response.addCookie(cookie);

            return "redirect:http://localhost:8082/api/auth/home";

        } catch (Exception e) {
            model.addAttribute("error", "Sai tài khoản hoặc mật khẩu!");
            return "login";
        }
    }

    @GetMapping("/register")
    public String showRegisterForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam(required = false) String birthday,
                           @RequestParam(required = false) String address,
                           Model model) {

        try {
            CreateUserRequest request = new CreateUserRequest();
            request.setUsername(username);
            request.setEmail(email);
            request.setDateOfBirth(birthday);
            request.setAddress(address);
            request.setFirstName(firstName);
            request.setLastName(lastName);

            User saveUser = userService.createUser(request);
            return "redirect:http://localhost:80/notification-success?userId="
                    + saveUser.getId() + "&email=" + saveUser.getEmail();

        } catch (Exception e) {
            model.addAttribute("error", "Đăng ký thất bại!");
            return "register";
        }
    }

    @GetMapping("/home")
    public String home(HttpServletRequest request, Model model) {

        String token = null;

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    token = cookie.getValue();
                }
            }
        }

        if (token != null) {
            String username = this.userService.extractUsername(token);
            model.addAttribute("username", username);
            model.addAttribute("isLoggedIn", true);
        } else {
            model.addAttribute("isLoggedIn", false);
        }

        return "home";
    }

    @GetMapping("/logout")
    public String logout(HttpServletResponse response) {

        Cookie cookie = new Cookie("accessToken", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        response.addCookie(cookie);

        return "redirect:http://localhost:8082/api/auth/login";
    }

    @GetMapping("/callbackGoogle")
    public void callback(@RequestParam String code,
                         HttpServletResponse response) throws IOException {

        TokenUserResponse token = userService.handleOAuth2Login(code);

        String accessToken = token.getAccess_token();

        Cookie cookie = new Cookie("accessToken", accessToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(3600);

        response.addCookie(cookie);

        response.sendRedirect("http://localhost:8082/api/auth/home");
    }

    @GetMapping("/notification-success")
    public String view(@RequestParam Long userId,
                       @RequestParam String email,
                       Model model) {
        model.addAttribute("userId", userId);
        model.addAttribute("email", email);
        return "notification-success";
    }

}
