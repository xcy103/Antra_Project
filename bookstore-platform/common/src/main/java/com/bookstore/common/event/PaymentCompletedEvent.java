package com.bookstore.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by payment-service when a payment succeeds. {@code eventId} is a
 * stable UUID for idempotent consumption.
 */
public record PaymentCompletedEvent(
        String eventId,
        Long orderId,
        BigDecimal amount,
        Instant occurredAt
) {
}
