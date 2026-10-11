package com.thang.user.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thang.user.model.dto.MessageResponseUser;
import com.thang.user.model.entity.AuthActivationOutbox;
import com.thang.user.repository.AuthActivationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ActivationOutboxService {
    private static final long LEASE_MILLIS = 60_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AuthActivationOutboxRepository outbox;
    private final SessionTokenCipher cipher;

    public record Delivery(String id, String claim, String userId, String payload, long expiresAt, int attempts) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(MessageResponseUser message, LocalDateTime expiresAt) {
        AuthActivationOutbox row = new AuthActivationOutbox();
        row.setId(UUID.randomUUID().toString());
        row.setUserId(message.getToUserId());
        try {
            row.setEncryptedPayload(cipher.encrypt(message.getToUserId(), JSON.writeValueAsString(message)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Cannot prepare activation notification", error);
        }
        row.setExpiresAt(expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        row.setAvailableAt(System.currentTimeMillis());
        outbox.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Delivery claim() {
        long now = System.currentTimeMillis();
        var rows = outbox.next(now, PageRequest.of(0, 1));
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        // Recheck after waiting for a competing publisher's row lock.
        now = System.currentTimeMillis();
        if (row.getClaimedUntil() > now || row.getExpiresAt() <= now) return null;
        row.setClaimToken(UUID.randomUUID().toString());
        row.setClaimedUntil(now + LEASE_MILLIS);
        return new Delivery(row.getId(), row.getClaimToken(), row.getUserId(), row.getEncryptedPayload(),
                row.getExpiresAt(), row.getAttempts());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acknowledge(Delivery delivery) { outbox.acknowledge(delivery.id(), delivery.claim()); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(Delivery delivery) {
        long delay = Math.min(30_000, 1_000L << Math.min(5, delivery.attempts()));
        outbox.retry(delivery.id(), delivery.claim(), System.currentTimeMillis() + delay);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cleanup() { outbox.removeExpired(System.currentTimeMillis()); }
}
