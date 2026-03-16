package com.thang.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final RevokedTokenService revokedTokenService;

    public AdminController(RevokedTokenService revokedTokenService) {
        this.revokedTokenService = revokedTokenService;
    }

    /**
     * Revoke by jti with TTL seconds.
     * Example: POST /admin/revoke?jti=abc123&ttlSeconds=300
     */
    @PostMapping("/revoke")
    public ResponseEntity<?> revoke(@RequestParam String jti, @RequestParam long ttlSeconds) {
        if (jti == null || jti.isBlank()) {
            return ResponseEntity.badRequest().body("jti required");
        }
        revokedTokenService.revoke(jti, Duration.ofSeconds(ttlSeconds));
        return ResponseEntity.ok().body("revoked");
    }
}