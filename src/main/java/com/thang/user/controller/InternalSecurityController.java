package com.thang.user.controller;
import com.nihongo.security.*;
import com.thang.user.service.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/api/auth/internal") @RequiredArgsConstructor
public class InternalSecurityController {
 private final InternalCredentials credentials;private final SessionService sessions;private final RateLimitService limits;
 private void require(String key){if(!credentials.matches(key))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Internal authentication required");}
 @PostMapping("/sessions/check") public List<String> sessions(@RequestHeader(value=InternalCredentials.HEADER,required=false) String key,@RequestBody List<RemoteSessionVerifier.SessionRef> refs){require(key);if(refs==null||refs.isEmpty()||refs.size()>1000||refs.stream().anyMatch(r->r==null||r.userId()==null||r.userId().length()>36||r.sessionId()==null||r.sessionId().length()>36))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid session batch");return sessions.invalidSessions(refs);}
 @PostMapping("/rate-limit") public RateLimitService.Result rate(@RequestHeader(value=InternalCredentials.HEADER,required=false) String key,@RequestBody List<RateLimitService.Limit> limits){require(key);if(limits==null||limits.isEmpty()||limits.size()>3||limits.stream().anyMatch(l->l==null))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid rate-limit request");try{return this.limits.consumeAll(limits);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid rate-limit request");}}
}
