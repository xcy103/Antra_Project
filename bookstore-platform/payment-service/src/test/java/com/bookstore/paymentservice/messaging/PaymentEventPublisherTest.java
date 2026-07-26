package com.bookstore.paymentservice.messaging;

import com.bookstore.common.event.PaymentCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private PaymentEventPublisher publisher;

    @Test
    void publishesToPaymentCompletedTopicKeyedByOrderId() {
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        PaymentCompletedEvent event = new PaymentCompletedEvent(
                "e1", 42L, new BigDecimal("29.99"), Instant.now());
        publisher.publishPaymentCompleted(event);

        verify(kafkaTemplate).send("payment-completed", "42", event);
    }
}
