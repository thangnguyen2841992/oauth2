package com.thang.user.service.user;

import com.nihongo.security.InternalCredentials;
import com.nihongo.security.LoginAddressEnvelope;
import com.nihongo.security.SecurityRequestPaths;
import com.thang.user.model.entity.LoginAudit;
import com.thang.user.model.entity.User;
import com.thang.user.repository.LoginAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.net.URI;
import java.time.LocalDateTime;

@Service
public class LoginAuditService {
    private final LoginAuditRepository audits;
    private final LoginAddressEnvelope envelope;
    public LoginAuditService(LoginAuditRepository audits, InternalCredentials credentials) {
        this.audits = audits;
        this.envelope = new LoginAddressEnvelope(credentials);
    }

    @Transactional
    public void record(User user, String method) {
        var row = new LoginAudit();
        row.setUserId(user.getUserId());
        row.setMethod(method);
        row.setCreatedAt(LocalDateTime.now());
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var request = attributes.getRequest();
            row.setAddress(clean(request.getRemoteAddr(), 64));
            row.setAgent(clean(request.getHeader("User-Agent"), 200));
            try {
                String path = SecurityRequestPaths.canonical(URI.create(request.getRequestURI()));
                envelope.verify(request.getHeader(LoginAddressEnvelope.HEADER), request.getMethod(), path).ifPresent(address -> {
                    row.setAddress(address.ip());
                    row.setLocalAddresses(address.localAddresses().isEmpty() ? null : address.localAddresses());
                });
            } catch (IllegalArgumentException ignored) { /* Retain the transport address for malformed metadata. */ }
        }
        audits.save(row);
    }

    private String clean(String text, int max) {
        if (text == null) return "";
        text = text.replaceAll("[\\p{Cntrl}]", "");
        return text.substring(0, Math.min(max, text.length()));
    }
    @Scheduled(fixedDelay = 86400000)
    @Transactional
    public void cleanup() { audits.cleanup(LocalDateTime.now().minusDays(90)); }
}
