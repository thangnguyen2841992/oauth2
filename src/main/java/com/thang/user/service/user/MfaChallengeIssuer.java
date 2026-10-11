package com.thang.user.service.user;
import com.thang.user.model.entity.*;import com.thang.user.repository.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;import java.security.SecureRandom;import java.util.Base64;
@Service @RequiredArgsConstructor
public class MfaChallengeIssuer {
 private final AuthMfaChallengeRepository challenges;
 @Transactional
 public String issue(User user){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);var c=new AuthMfaChallenge();c.setId(PasswordSetupService.hash(token));c.setUserId(user.getUserId());c.setRole(user.getRoleName());c.setCredentialFingerprint(PasswordSetupService.hash(user.getPassword()==null?"":user.getPassword()));c.setExpiresAt(System.currentTimeMillis()+300000);challenges.save(c);return token;}
}
