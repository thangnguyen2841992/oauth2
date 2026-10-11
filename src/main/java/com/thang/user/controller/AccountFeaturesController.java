package com.thang.user.controller;
import com.thang.user.service.user.*;import com.thang.user.repository.LoginAuditRepository;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.data.domain.PageRequest;import org.springframework.web.server.ResponseStatusException;import org.springframework.http.HttpStatus;import java.util.*;
@RestController @RequestMapping("/api/users/me") @RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
public class AccountFeaturesController {
 private final AccountProfileService profiles;private final AuthMfaService mfa;private final LoginAuditRepository history;private final SessionService sessions;
 public record Code(String code){} public record Proof(String currentPassword,String code){}
 @GetMapping("/profile") public AccountProfileService.Profile profile(@AuthenticationPrincipal Jwt jwt){return profiles.profile(jwt.getSubject());}
 @PutMapping("/profile") public AccountProfileService.Profile profile(@AuthenticationPrincipal Jwt jwt,@RequestBody AccountProfileService.Profile input){return profiles.save(jwt.getSubject(),input);}
 @PostMapping("/change-password") public void password(@AuthenticationPrincipal Jwt jwt,@RequestBody AccountProfileService.ChangePassword input){profiles.changePassword(jwt.getSubject(),input);}
 @GetMapping("/security") public AuthMfaService.Status security(@AuthenticationPrincipal Jwt jwt){return mfa.status(jwt.getSubject());}
 @PostMapping("/mfa/setup") public AuthMfaService.Setup setup(@AuthenticationPrincipal Jwt jwt,@RequestBody Proof proof){return mfa.setup(jwt.getSubject(),proof.currentPassword());}
 @PostMapping("/mfa/enable") public List<String> enable(@AuthenticationPrincipal Jwt jwt,@RequestBody Code code){return mfa.enable(jwt.getSubject(),code.code());}
 @PostMapping("/mfa/disable") public void disable(@AuthenticationPrincipal Jwt jwt,@RequestBody Proof proof){mfa.disable(jwt.getSubject(),proof.currentPassword(),proof.code());}
 @PostMapping("/mfa/backups") public List<String> backups(@AuthenticationPrincipal Jwt jwt,@RequestBody Proof proof){return mfa.newBackups(jwt.getSubject(),proof.currentPassword(),proof.code());}
 @GetMapping("/logins") public Map<String,Object> history(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page){if(page<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trang không hợp lệ");var result=history.findByUserIdOrderByCreatedAtDescIdDesc(jwt.getSubject(),PageRequest.of(page,20));return Map.of("content",result.getContent(),"number",result.getNumber(),"totalElements",result.getTotalElements(),"totalPages",result.getTotalPages());}
 @PostMapping("/revoke-session") public void revoke(@AuthenticationPrincipal Jwt jwt){sessions.removeSession(jwt.getSubject());}
}
