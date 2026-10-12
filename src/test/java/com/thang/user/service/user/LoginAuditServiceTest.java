package com.thang.user.service.user;

import com.nihongo.security.*;
import com.thang.user.model.entity.*;
import com.thang.user.repository.LoginAuditRepository;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAuditServiceTest {
    InternalCredentials key() { return new InternalCredentials(new MockEnvironment().withProperty("security.internal.key", Base64.getEncoder().encodeToString(new byte[32]))); }
    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    LoginAudit record(String header) {
        var request = new MockHttpServletRequest("POST", "/api/auth/login"); request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "Recorded browser"); request.addHeader("X-Forwarded-For", "8.8.8.8");
        if (header != null) request.addHeader(LoginAddressEnvelope.HEADER, header);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var repository = mock(LoginAuditRepository.class); var user = new User(); user.setUserId("user-1");
        new LoginAuditService(repository, key()).record(user, "PASSWORD");
        var captured = ArgumentCaptor.forClass(LoginAudit.class); verify(repository).save(captured.capture()); return captured.getValue();
    }
    @Test void recordsSignedClientIpAndLocalSnapshot() {
        var row = record(new LoginAddressEnvelope(key()).sign("POST", "/api/auth/login", "::1", "192.168.1.8"));
        assertEquals("::1", row.getAddress()); assertEquals("192.168.1.8", row.getLocalAddresses()); assertEquals("Recorded browser", row.getAgent());
        row = record(new LoginAddressEnvelope(key()).sign("POST", "/api/auth/login", "192.168.1.9", ""));
        assertEquals("192.168.1.9", row.getAddress()); assertNull(row.getLocalAddresses());
    }
    @Test void untrustedHeadersCannotReplaceTransportIp() {
        for (String header : new String[]{null, "forged", new LoginAddressEnvelope(key()).sign("GET", "/api/auth/login", "192.168.1.9", "")}) {
            var row = record(header); assertEquals("127.0.0.1", row.getAddress()); assertNull(row.getLocalAddresses());
        }
    }
}
