package com.bookstore.orderservice.messaging;

import com.bookstore.common.event.KafkaTopics;
import com.bookstore.common.event.PaymentCompletedEvent;
import com.bookstore.orderservice.service.PaymentEventHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * order-service consumes PaymentCompleted on its own consumer group so a paid
 * order transitions PENDING -> PAID. This closes the loop with payment-service,
 * which only publishes the event; the actual state change lives here because
 * order-service owns the order. Idempotency is handled in {@link PaymentEventHandler}.
 */
@Component
public class PaymentEventConsumer {

    private final PaymentEventHandler handler;

    public PaymentEventConsumer(PaymentEventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        handler.handlePaymentCompleted(event);
    }
}
