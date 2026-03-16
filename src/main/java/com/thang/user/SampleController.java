package com.thang.user;


import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SampleController {

    @GetMapping("/public/hello")
    public Map<String, String> publicHello() {
        return Map.of("msg", "Hello public");
    }

    @GetMapping("/api/userinfo")
    @PreAuthorize("hasAuthority('SCOPE_profile') or hasRole('ADMIN')")
    public Map<String, Object> userInfo(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "sub", jwt.getSubject(),
                "claims", jwt.getClaims()
        );
    }
}