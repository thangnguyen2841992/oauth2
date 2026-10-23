package com.thang.user.service.user;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ActivationOutboxIdleTest {
    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    final ActivationOutboxService queue = mock(ActivationOutboxService.class);

    @Test void emptyQueueIsNotPolledAgainUntilWokenByANewActivation() {
        when(queue.claim()).thenReturn(null);
        when(queue.idleMillis(anyLong())).thenReturn(30_000L);
        var publisher = new ActivationOutboxPublisher(queue, kafka, mock(SessionTokenCipher.class));
        publisher.publishPending();
        publisher.publishPending();
        publisher.publishPending();
        verify(queue, times(1)).claim();
        publisher.queued(new ActivationOutboxService.Queued());
        publisher.publishPending();
        verify(queue, times(2)).claim();
        verifyNoInteractions(kafka);
    }

    @Test void dueRetrySoonerThanTheIdleLimitIsPolledAgain() {
        when(queue.claim()).thenReturn(null);
        when(queue.idleMillis(anyLong())).thenReturn(0L);
        var publisher = new ActivationOutboxPublisher(queue, kafka, mock(SessionTokenCipher.class));
        publisher.publishPending();
        publisher.publishPending();
        verify(queue, times(2)).claim();
    }
}
