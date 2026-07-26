package com.bookstore.notificationservice.integration;

import com.bookstore.common.event.KafkaTopics;
import com.bookstore.common.event.OrderPlacedEvent;
import com.bookstore.notificationservice.repository.ProcessedEventRepository;
import com.bookstore.notificationservice.support.AbstractKafkaPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The core Kafka test: the same event delivered twice (Kafka is at-least-once)
 * must be processed exactly once — proven by a single row in the idempotency
 * ledger.
 */
@SpringBootTest
class NotificationIdempotencyTest extends AbstractKafkaPostgresIT {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @BeforeEach
    void clean() {
        processedEvents.deleteAll();
    }

    @Test
    void sameEventDeliveredTwice_isProcessedOnce() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "dup-order-1", 7L, "alice", new BigDecimal("10.00"), Instant.now());

        // Deliver the identical event (same eventId) twice.
        kafkaTemplate.send(KafkaTopics.ORDER_PLACED, String.valueOf(event.orderId()), event);
        kafkaTemplate.send(KafkaTopics.ORDER_PLACED, String.valueOf(event.orderId()), event);

        // Exactly one ledger row — the redelivery was recognized and skipped.
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(processedEvents.count()).isEqualTo(1L));

        // Give the second delivery time to be consumed, then confirm it stayed at 1.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(processedEvents.count()).isEqualTo(1L));
    }
}
