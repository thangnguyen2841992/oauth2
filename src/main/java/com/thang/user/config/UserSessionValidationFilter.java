package com.thang.user.config;
import com.thang.user.service.user.SessionService;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;
public class UserSessionValidationFilter extends OncePerRequestFilter {
 private final SessionService sessions;private final boolean enabled;
 public UserSessionValidationFilter(SessionService sessions,boolean enabled){this.sessions=sessions;this.enabled=enabled;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  var auth=SecurityContextHolder.getContext().getAuthentication();if(!enabled||!(auth instanceof JwtAuthenticationToken jwt)){chain.doFilter(request,response);return;}
  try{var token=jwt.getToken();if("access".equals(token.getClaimAsString("type"))&&sessions.roleMatches(token.getSubject(),token.getClaimAsString("sessionId"),token.getClaimAsStringList("roles"))){chain.doFilter(request,response);return;}response.setStatus(401);response.setHeader("X-Auth-Failure","session-invalid");}
  catch(org.springframework.dao.DataAccessException e){response.setStatus(503);}
 }
}
