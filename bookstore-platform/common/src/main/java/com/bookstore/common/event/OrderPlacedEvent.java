package com.bookstore.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by order-service when an order is placed. {@code eventId} is a stable
 * UUID generated once by the producer — consumers dedupe on it, so a Kafka
 * redelivery (at-least-once) is processed only once.
 */
public record OrderPlacedEvent(
        String eventId,
        Long orderId,
        String username,
        BigDecimal totalPrice,
        Instant occurredAt
) {
}
