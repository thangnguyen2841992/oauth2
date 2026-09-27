package com.thang.user.service.user;
import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PasswordSetupTest {
 IUserRepository users=mock(IUserRepository.class);
 PasswordEncoder encoder=mock(PasswordEncoder.class);
 SessionService sessions=mock(SessionService.class);
 PasswordSetupService service=new PasswordSetupService(users,encoder,sessions);
 User owner;
 @BeforeEach void setup(){owner=new User();owner.setUserId("owner");owner.setEmail("owner@example.com");owner.setCodeActive("valid-code");owner.setCodeActiveExpiredAt(LocalDateTime.now().plusHours(1));when(users.lockById("owner")).thenReturn(Optional.of(owner));}
 @Test void activationIssuesHashedOneUseToken(){
  var result=service.activate("owner","valid-code");
  assertEquals("SUCCESS",result.get("status"));assertTrue(owner.isActive());assertNull(owner.getCodeActive());
  assertNotEquals(result.get("setupToken"),owner.getPasswordSetupHash());
  when(encoder.encode("ValidPass1!")).thenReturn("hashed");
  var request=new PasswordSetupService.SetupRequest("owner",result.get("setupToken"),"ValidPass1!","ValidPass1!");
  service.reset(request);assertEquals("hashed",owner.getPassword());assertNull(owner.getPasswordSetupHash());
  assertThrows(ResponseStatusException.class,()->service.reset(request));verify(sessions,times(1)).removeSession("owner");
 }
 @Test void emailOnlyCannotChangePassword(){
  assertThrows(ResponseStatusException.class,()->service.reset(new PasswordSetupService.SetupRequest(null,null,"ValidPass1!","ValidPass1!")));
  verifyNoInteractions(encoder,sessions);
 }
 @Test void wrongExpiredAndOtherAccountTokensAreRejected(){
  var token=service.activate("owner","valid-code").get("setupToken");
  assertThrows(ResponseStatusException.class,()->service.reset(new PasswordSetupService.SetupRequest("owner","wrong","ValidPass1!","ValidPass1!")));
  assertThrows(ResponseStatusException.class,()->service.reset(new PasswordSetupService.SetupRequest("other",token,"ValidPass1!","ValidPass1!")));
  owner.setPasswordSetupExpiresAt(LocalDateTime.now().minusSeconds(1));
  assertThrows(ResponseStatusException.class,()->service.reset(new PasswordSetupService.SetupRequest("owner",token,"ValidPass1!","ValidPass1!")));
  verifyNoInteractions(encoder);
 }
 @Test void invalidOrExpiredActivationCannotIssueToken(){
  assertThrows(ResponseStatusException.class,()->service.activate("owner","wrong"));
  owner.setCodeActiveExpiredAt(LocalDateTime.now().minusMinutes(1));
  assertEquals("EXPIRED",service.activate("owner","valid-code").get("status"));assertFalse(owner.isActive());assertNull(owner.getPasswordSetupHash());
 }
}
