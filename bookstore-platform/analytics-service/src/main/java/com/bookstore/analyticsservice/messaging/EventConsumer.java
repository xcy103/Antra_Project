package com.bookstore.analyticsservice.messaging;

import com.bookstore.analyticsservice.service.AnalyticsHandler;
import com.bookstore.common.event.KafkaTopics;
import com.bookstore.common.event.OrderPlacedEvent;
import com.bookstore.common.event.PaymentCompletedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the same order/payment topics as notification-service, but under the
 * {@code analytics-service} consumer group — so both services independently
 * receive every event.
 */
@Component
public class EventConsumer {

    private final AnalyticsHandler handler;

    public EventConsumer(AnalyticsHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_PLACED)
    public void onOrderPlaced(OrderPlacedEvent event) {
        handler.handleOrderPlaced(event);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        handler.handlePaymentCompleted(event);
    }
}
