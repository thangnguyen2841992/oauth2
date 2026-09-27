package com.thang.user.controller;
import com.thang.user.service.user.IUserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringJUnitConfig(UserPrivacyTest.Config.class)
class UserPrivacyTest {
 @Configuration @EnableMethodSecurity static class Config {
  @Bean IUserService service(){return mock(IUserService.class);}
  @Bean UserRestController controller(IUserService service){return new UserRestController(service);}
 }
 @Autowired UserRestController controller; @Autowired IUserService service;
 @BeforeEach void setup(){reset(service);}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 void login(String role){
  var jwt=Jwt.withTokenValue("test").header("alg","HS256").subject("owner").claim("email","owner@example.com").build();
  SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
 }
 @Test void userCanReadSelfButNotOtherEmail(){
  login("USER");controller.findUserByEmail("owner@example.com");verify(service).findUserByEmailDTO("owner@example.com");
  assertThrows(AccessDeniedException.class,()->controller.findUserByEmail("other@example.com"));
  verify(service,never()).findUserByEmailDTO("other@example.com");
 }
 @Test void adminMayLookUpAnotherUser(){login("ADMIN");controller.findUserByEmail("other@example.com");verify(service).findUserByEmailDTO("other@example.com");}
}
