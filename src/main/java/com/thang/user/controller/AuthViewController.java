package com.thang.user.controller;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.service.user.IUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/active-user")
@Controller
public class AuthViewController {
    private final IUserService userService;

    public AuthViewController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/active")
    public String activeAccount(@RequestParam long userId,
                                @RequestParam String activeCode,
                                @RequestParam String email,
                                Model model) {

        String result = userService.activeUser(userId, activeCode);

        return switch (result) {
            case "SUCCESS" -> "redirect:http://localhost:8082/api/active-user/updatePassword?email="
                    + email;
            case "EXPIRED" -> {
                model.addAttribute("userId", userId);
                yield "active-expired";
            }
            case "INVALID" -> "active-failed";
            case "ALREADY_ACTIVE" -> "active-already";
            default -> "error";
        };
    }

    @GetMapping("/updatePassword")
    public String resetPage(@RequestParam("email") String email, Model model) {
        model.addAttribute("email", email);
        return "reset-password";
    }


    @PostMapping("/updatePassword")
    public String updatePassword(
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam String email
    ) {
        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setEmail(email);
        userRequest.setPassword(password);
        userRequest.setConfirmPassword(confirmPassword);
        String result = this.userService.updatePassword(userRequest);
        if (result.equals("SUCCESS")) {
            return "redirect:http://localhost:8082/api/auth/login";
        }
        return "reset-password";
    }


}
