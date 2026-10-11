package com.thang.user.controller;
import com.thang.user.service.user.*;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.http.*;import java.util.*;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AccountRecoveryController {
 private final AccountRecoveryService recovery;private final AuthMfaService mfa;
 @Value("${COOKIE_SECURE:true}") private boolean secure;
 public record Email(String email){} public record Code(String code){}
 @PostMapping("/forgot-password") public Map<String,String> forgot(@RequestBody Email input){recovery.request(input.email());return Map.of("message","Nếu tài khoản hợp lệ, liên kết đặt lại mật khẩu sẽ được gửi qua email.");}
 @PostMapping("/recover-password") public void reset(@RequestBody AccountRecoveryService.Reset input){recovery.reset(input);}
 @PostMapping("/mfa/verify") public ResponseEntity<?> verify(@CookieValue(value="mfaChallenge",required=false) String challenge,@RequestBody Code input){var result=mfa.verify(challenge,input.code());if(result.tokens()==null)return ResponseEntity.status(result.retryAfter()>0?429:401).header("Retry-After",Long.toString(Math.max(1,result.retryAfter()))).body(Map.of("message","Mã không hợp lệ, đã dùng hoặc phiên xác minh hết hạn. Hãy thử lại sau."));var tokens=result.tokens();return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie("accessToken",tokens.getAccess_token(),Math.max(0,(tokens.getAccessExpiresAt()-System.currentTimeMillis())/1000)),cookie("refreshToken",tokens.getRefresh_token(),Math.max(0,(tokens.getRefreshExpiresAt()-System.currentTimeMillis())/1000)),cookie("mfaChallenge","",0)).body(Map.of("message","Đăng nhập thành công"));}
 @PostMapping("/mfa/cancel") public ResponseEntity<Void> cancel(@CookieValue(value="mfaChallenge",required=false) String challenge){mfa.cancel(challenge);return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookie("mfaChallenge","",0)).build();}
 private String cookie(String name,String value,long age){return ResponseCookie.from(name,value).path("/").httpOnly(true).secure(secure).sameSite("Lax").maxAge(age).build().toString();}
}
