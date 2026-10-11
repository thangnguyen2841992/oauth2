package com.thang.user.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thang.user.model.dto.MessageResponseUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "auth.activation-outbox.enabled", havingValue = "true", matchIfMissing = true)
public class ActivationOutboxPublisher {
    private static final org.slf4j.Logger events = org.slf4j.LoggerFactory.getLogger("nihongo.kafka.activation");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ActivationOutboxService outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final SessionTokenCipher cipher;

    @Scheduled(fixedDelayString = "${auth.activation-outbox.poll-ms:1000}")
    public void publishPending() {
        // Claim one at a time: queued messages never spend their lease waiting behind a slow send.
        for (int i = 0; i < 5 && !Thread.currentThread().isInterrupted(); i++) {
            var delivery = outbox.claim();
            if (delivery == null) return;
            try {
                if (delivery.expiresAt() > System.currentTimeMillis()) {
                    var message = JSON.readValue(cipher.decrypt(delivery.userId(), delivery.payload()), MessageResponseUser.class);
                    var sent = kafka.send("send-email-active-response", delivery.userId(), message).get(20, TimeUnit.SECONDS);
                    var metadata = sent == null ? null : sent.getRecordMetadata();
                    events.info("event=published topic=send-email-active-response deliveryId={} partition={} offset={} attempt={}",
                            delivery.id(), metadata == null ? -1 : metadata.partition(),
                            metadata == null ? -1 : metadata.offset(), delivery.attempts() + 1);
                }
                outbox.acknowledge(delivery);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return; // Another publisher may recover the lease after shutdown.
            } catch (Exception unavailable) {
                outbox.retry(delivery);
                events.warn("event=retry topic=send-email-active-response deliveryId={} attempt={} failure={}", delivery.id(), delivery.attempts() + 1, unavailable.getClass().getSimpleName());
                return;
            }
        }
    }
}
