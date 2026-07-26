package com.bookstore.orderservice.messaging;

import com.bookstore.common.event.OrderPlacedEvent;
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
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderEventPublisher publisher;

    @Test
    void publishesToOrderPlacedTopicKeyedByOrderId() {
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        OrderPlacedEvent event = new OrderPlacedEvent(
                "e1", 42L, "alice", new BigDecimal("10.00"), Instant.now());
        publisher.publishOrderPlaced(event);

        // Keyed by orderId ("42") so events for one order keep partition order.
        verify(kafkaTemplate).send("order-placed", "42", event);
    }
}
