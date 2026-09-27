package com.thang.user.controller;
import com.thang.user.service.user.PasswordSetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/active-user") @RequiredArgsConstructor
public class AuthViewController {
    private final PasswordSetupService passwords;
    @GetMapping("/active")
    public Map<String,String> activeAccount(@RequestParam String userId, @RequestParam String activeCode) {
        return passwords.activate(userId, activeCode);
    }
    @PostMapping("/updatePassword")
    public Map<String,String> updatePassword(@RequestBody PasswordSetupService.SetupRequest request) {
        passwords.reset(request); return Map.of("status","SUCCESS");
    }
}
