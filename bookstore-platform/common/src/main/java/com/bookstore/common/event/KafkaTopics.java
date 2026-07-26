package com.bookstore.common.event;

/**
 * Kafka topic names shared by producers and consumers. Events are keyed by
 * {@code orderId} so all events for one order land on the same partition and are
 * processed in order.
 */
public final class KafkaTopics {

    public static final String ORDER_PLACED = "order-placed";
    public static final String PAYMENT_COMPLETED = "payment-completed";

    private KafkaTopics() {
    }
}
