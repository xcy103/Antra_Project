package com.bookstore.analyticsservice.integration;

import com.bookstore.analyticsservice.entity.Metric;
import com.bookstore.analyticsservice.repository.MetricRepository;
import com.bookstore.analyticsservice.repository.ProcessedEventRepository;
import com.bookstore.analyticsservice.support.AbstractKafkaPostgresIT;
import com.bookstore.common.event.KafkaTopics;
import com.bookstore.common.event.OrderPlacedEvent;
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
 * Idempotent aggregation: the same OrderPlaced delivered twice must bump the
 * "orders" metric exactly once (count 1, revenue = one order's total).
 */
@SpringBootTest
class AnalyticsIdempotencyTest extends AbstractKafkaPostgresIT {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private MetricRepository metrics;
    @Autowired
    private ProcessedEventRepository processedEvents;

    @BeforeEach
    void clean() {
        metrics.deleteAll();
        processedEvents.deleteAll();
    }

    @Test
    void sameOrderEventTwice_countedOnce() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "dup-analytics-1", 7L, "alice", new BigDecimal("12.50"), Instant.now());

        kafkaTemplate.send(KafkaTopics.ORDER_PLACED, String.valueOf(event.orderId()), event);
        kafkaTemplate.send(KafkaTopics.ORDER_PLACED, String.valueOf(event.orderId()), event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Metric orders = metrics.findById("orders").orElse(null);
            assertThat(orders).isNotNull();
            assertThat(orders.getTotalCount()).isEqualTo(1L);
            assertThat(orders.getTotalAmount()).isEqualByComparingTo("12.50");
        });
    }
}
