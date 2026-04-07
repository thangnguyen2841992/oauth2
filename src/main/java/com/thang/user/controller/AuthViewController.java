package com.thang.user.controller;

import com.thang.user.service.user.IUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

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
            case "SUCCESS" -> "redirect:http://localhost:8082/api/active-user/active-success?userId="
                    + userId + "&email=" + email;
            case "EXPIRED" -> {
                model.addAttribute("userId", userId);
                yield "active-expired";
            }
            case "INVALID" -> "active-failed";
            case "ALREADY_ACTIVE" -> "active-already";
            default -> "error";
        };
    }

    @GetMapping("/active-success")
    public String viewActiveSuccess(@RequestParam Long userId,
                                    @RequestParam String email,
                                    Model model) {
        model.addAttribute("userId", userId);
        model.addAttribute("email", email);
        return "active-success";
    }

    @PostMapping("/resend-active")
    @ResponseBody
    public ResponseEntity<String> resendActive(@RequestParam(required = false) Long userId) {

        if (userId == null) {
            return ResponseEntity.badRequest().body("Missing userId");
        }

        return ResponseEntity.ok(userService.resendActiveCode(userId));
    }


}
