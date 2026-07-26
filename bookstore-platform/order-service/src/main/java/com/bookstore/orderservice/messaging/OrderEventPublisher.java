package com.bookstore.orderservice.messaging;

import com.bookstore.common.event.KafkaTopics;
import com.bookstore.common.event.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link OrderPlacedEvent}s. Events are keyed by {@code orderId} so all
 * events for one order share a partition (ordering). Publishing is non-blocking —
 * a broker hiccup must not fail the order request (delivery guarantees are the
 * consumer's job via idempotency; an outbox for exactly-once producing is a
 * documented challenge).
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderPlaced(OrderPlacedEvent event) {
        kafkaTemplate.send(KafkaTopics.ORDER_PLACED, String.valueOf(event.orderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderPlaced for order {}: {}",
                                event.orderId(), ex.toString());
                    } else {
                        log.info("Published OrderPlaced eventId={} order={}",
                                event.eventId(), event.orderId());
                    }
                });
    }
}
