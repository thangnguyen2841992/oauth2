package com.thang.user.security;

import com.thang.user.UserServiceApplication;
import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.MessageResponseUser;
import com.thang.user.repository.AuthActivationOutboxRepository;
import com.thang.user.repository.IUserRepository;
import com.thang.user.service.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = UserServiceApplication.class, properties = {
        "spring.profiles.active=local,sql-trace",
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:activation_outbox;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false", "spring.kafka.admin.auto-create=false",
        "app.bootstrap.accounts-enabled=false", "auth.activation-outbox.enabled=false"
})
class ActivationOutboxIntegrationTest {
    @Autowired IUserService users;
    @Autowired IUserRepository accounts;
    @Autowired AuthActivationOutboxRepository rows;
    @Autowired ActivationOutboxService queue;
    @Autowired SessionTokenCipher cipher;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean KafkaTemplate<String, Object> kafka;
    @MockitoBean SimpMessagingTemplate sockets;

    @BeforeEach void reset() { rows.deleteAll(); accounts.deleteAll(); clearInvocations(kafka, sockets); }
    CreateUserRequest registration() {
        var input = new CreateUserRequest();
        input.setEmail(UUID.randomUUID() + "@example.test"); input.setFirstName("Test"); input.setLastName("Account");
        input.setPassword("Strong1!pass"); input.setConfirmPassword("Strong1!pass");
        return input;
    }
    ActivationOutboxPublisher publisher() { return new ActivationOutboxPublisher(queue, kafka, cipher); }

    @Test void rollbackDiscardsTheEmailAndTheAccount() {
        var input = registration();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            users.createUser(input);
            assertEquals(1, rows.count()); verifyNoInteractions(kafka);
            tx.setRollbackOnly();
        });
        assertEquals(0, rows.count()); assertFalse(accounts.existsByEmail(input.getEmail())); verifyNoInteractions(kafka);
    }

    @Test void onlyCommittedEmailIsSentOutsideTheDatabaseTransaction() {
        var account = users.createUser(registration());
        assertEquals(1, rows.count()); verifyNoInteractions(kafka);
        assertFalse(rows.findAll().get(0).getEncryptedPayload().contains(account.getCodeActive()));
        when(kafka.send(eq("send-email-active-response"), eq(account.getUserId()), any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            MessageResponseUser message = invocation.getArgument(2);
            assertEquals(account.getCodeActive(), message.getActiveCode());
            assertEquals(account.getEmail(), message.getToUserEmail());
            assertEquals(account.getUserId(), message.getToUserId());
            return CompletableFuture.completedFuture(null);
        });
        publisher().publishPending();
        assertEquals(0, rows.count());
        verify(kafka).send(eq("send-email-active-response"), eq(account.getUserId()), any());
    }

    @Test void brokerFailureKeepsTheCommittedMessageForRetry() {
        var account = users.createUser(registration());
        when(kafka.send(eq("send-email-active-response"), eq(account.getUserId()), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        publisher().publishPending();
        var row = rows.findAll().get(0); assertEquals(1, row.getAttempts()); assertNull(row.getClaimToken());
        assertTrue(row.getAvailableAt() > System.currentTimeMillis());
        row.setAvailableAt(0); rows.saveAndFlush(row);
        when(kafka.send(eq("send-email-active-response"), eq(account.getUserId()), any())).thenReturn(CompletableFuture.completedFuture(null));
        publisher().publishPending(); assertEquals(0, rows.count());
        verify(kafka, times(2)).send(eq("send-email-active-response"), eq(account.getUserId()), any());
    }

    @Test void leasesRecoverAfterACrashAndStaleWorkersCannotDeleteAnotherClaim() {
        users.createUser(registration());
        var first = queue.claim(); assertNotNull(first); assertNull(queue.claim());
        var row = rows.findById(first.id()).orElseThrow(); row.setClaimedUntil(0); rows.saveAndFlush(row);
        var second = queue.claim(); assertNotNull(second); assertNotEquals(first.claim(), second.claim());
        queue.acknowledge(first); assertEquals(1, rows.count());
        queue.retry(first); assertEquals(second.claim(), rows.findById(first.id()).orElseThrow().getClaimToken());
        queue.acknowledge(second); assertEquals(0, rows.count());
    }

    @Test void expiredActivationCodesAreNeverPublished() {
        users.createUser(registration()); var row = rows.findAll().get(0);
        row.setExpiresAt(System.currentTimeMillis() - 1); rows.saveAndFlush(row);
        publisher().publishPending(); verifyNoInteractions(kafka);
        queue.cleanup(); assertEquals(0, rows.count());
    }

    @Test void logoutNotificationWaitsForCommitAndRollbackKeepsTheExistingBrowser() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            users.forceLogoutUser("owner", "old-session"); verifyNoInteractions(sockets); tx.setRollbackOnly();
        });
        verifyNoInteractions(sockets);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            users.forceLogoutUser("owner", "old-session"); verifyNoInteractions(sockets);
        });
        verify(sockets).convertAndSendToUser(eq("owner"), eq("/queue/logout"), any());
    }
}
