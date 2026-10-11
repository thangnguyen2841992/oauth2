package com.thang.user.service.user;
import com.thang.user.model.entity.*;import com.thang.user.repository.LoginAuditRepository;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.web.context.request.*;import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
public class LoginAuditService {
 private final LoginAuditRepository audits;
 @Transactional public void record(User user,String method){var row=new LoginAudit();row.setUserId(user.getUserId());row.setMethod(method);row.setCreatedAt(LocalDateTime.now());if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes){row.setAddress(clean(attributes.getRequest().getRemoteAddr(),64));row.setAgent(clean(attributes.getRequest().getHeader("User-Agent"),200));}audits.save(row);}
 private String clean(String text,int max){if(text==null)return "";text=text.replaceAll("[\\p{Cntrl}]","");return text.substring(0,Math.min(max,text.length()));}
 @Scheduled(fixedDelay=86400000) @Transactional public void cleanup(){audits.cleanup(LocalDateTime.now().minusDays(90));}
}
