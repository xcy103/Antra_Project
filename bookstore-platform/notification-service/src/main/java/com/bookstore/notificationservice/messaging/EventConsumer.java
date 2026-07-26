package com.bookstore.notificationservice.messaging;

import com.bookstore.common.event.KafkaTopics;
import com.bookstore.common.event.OrderPlacedEvent;
import com.bookstore.common.event.PaymentCompletedEvent;
import com.bookstore.notificationservice.service.NotificationHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order/payment events for the {@code notification-service} group.
 * analytics-service uses a different group, so both receive every event.
 */
@Component
public class EventConsumer {

    private final NotificationHandler handler;

    public EventConsumer(NotificationHandler handler) {
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
