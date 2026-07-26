package com.bookstore.paymentservice.messaging;

import com.bookstore.common.event.KafkaTopics;
import com.bookstore.common.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link PaymentCompletedEvent}s, keyed by {@code orderId}. Non-blocking:
 * a broker hiccup must not fail the payment request.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED, String.valueOf(event.orderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PaymentCompleted for order {}: {}",
                                event.orderId(), ex.toString());
                    } else {
                        log.info("Published PaymentCompleted eventId={} order={}",
                                event.eventId(), event.orderId());
                    }
                });
    }
}
