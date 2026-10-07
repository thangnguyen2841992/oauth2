package com.thang.user.controller;

import com.thang.user.model.entity.AdminAccountAudit;
import com.thang.user.service.user.AdminAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAccountController {
    private final AdminAccountService accounts;

    @GetMapping public List<AdminAccountService.Account> list() { return accounts.list(); }
    @PostMapping public AdminAccountService.Account invite(@AuthenticationPrincipal Jwt jwt,
            @RequestBody AdminAccountService.Invite request) { return accounts.invite(jwt.getSubject(), request); }
    @PatchMapping("/{id}/role") public AdminAccountService.Account role(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String id, @RequestBody AdminAccountService.RoleChange request) {
        return accounts.changeRole(jwt.getSubject(), id, request);
    }
    @PatchMapping("/{id}/status") public AdminAccountService.Account status(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String id, @RequestBody AdminAccountService.StatusChange request) {
        return accounts.changeStatus(jwt.getSubject(), id, request);
    }
    @GetMapping("/{id}/history") public List<AdminAccountAudit> history(@PathVariable String id) {
        return accounts.history(id);
    }
}
