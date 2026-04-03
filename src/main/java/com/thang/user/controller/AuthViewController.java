package com.thang.user.controller;

import com.thang.user.service.user.IUserService;
import org.springframework.stereotype.Component;
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
                                Model model) {

        String result = userService.activeUser(userId, activeCode);

        return switch (result) {
            case "SUCCESS" -> "active-success";
            case "EXPIRED" -> {
                model.addAttribute("userId", userId);
                yield "active-expired";
            }
            case "INVALID" -> "active-failed";
            case "ALREADY_ACTIVE" -> "active-already";
            default -> "error";
        };
    }

    @PostMapping("/resend-active")
    public String resendActive(@RequestParam long userId, Model model) {

        String message = userService.resendActiveCode(userId);

        model.addAttribute("message", message);

        return "resend-success";
    }


}
