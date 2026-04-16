package com.thang.user.controller;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.identity.ResendActiveRequest;
import com.thang.user.service.user.IUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/active-user")
public class AuthViewController {

    private final IUserService userService;
    private final Map<Long, Long> resendCache = new ConcurrentHashMap<>();


    public AuthViewController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/active")
    public ResponseEntity<?> activeAccount(@RequestParam long userId,
                                           @RequestParam String activeCode,
                                           @RequestParam String email) {

        String result = userService.activeUser(userId, activeCode);

        return switch (result) {
            case "SUCCESS" -> ResponseEntity.ok(
                    Map.of(
                            "status", "SUCCESS",
                            "redirect", "/reset-password",
                            "email", email
                    )
            );

            case "EXPIRED" -> ResponseEntity.ok(
                    Map.of("status", "EXPIRED", "userId", userId)
            );

            case "INVALID" -> ResponseEntity.ok(
                    Map.of("status", "INVALID")
            );

            case "ALREADY_ACTIVE" -> ResponseEntity.ok(
                    Map.of("status", "ALREADY_ACTIVE")
            );

            default -> ResponseEntity.status(500).build();
        };
    }

    @PostMapping("/updatePassword")
    public ResponseEntity<?> updatePassword(@RequestBody CreateUserRequest request) {

        String result = this.userService.updatePassword(request);

        if (result.equals("SUCCESS")) {
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        }

        return ResponseEntity.badRequest().body("Update failed");
    }

    @PostMapping("/resend-active")
    public ResponseEntity<?> resendActive(@RequestBody ResendActiveRequest request) {

        if (request.getUserId() == null) {
            return ResponseEntity.badRequest().body("Missing userId");
        }

        String result = userService.resendActiveCode(request.getUserId());

        return ResponseEntity.ok(
                Map.of(
                        "status", "SUCCESS",
                        "message", "Đã gửi lại email"
                )
        );
    }
}