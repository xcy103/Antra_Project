package com.bookstore.notificationservice.service;

import com.bookstore.common.event.OrderPlacedEvent;
import com.bookstore.common.event.PaymentCompletedEvent;
import com.bookstore.notificationservice.entity.ProcessedEvent;
import com.bookstore.notificationservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles events idempotently: the {@code processed_event} ledger (keyed by event
 * id) is checked before doing the work, so a redelivered event is skipped. Here
 * the "work" is sending a notification (logged); the ledger row is the side effect
 * the idempotency test asserts on.
 */
@Service
public class NotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationHandler.class);

    private final ProcessedEventRepository processedEvents;

    public NotificationHandler(ProcessedEventRepository processedEvents) {
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (alreadyProcessed(event.eventId())) {
            return;
        }
        log.info("Notification: order {} placed by {} (total {})",
                event.orderId(), event.username(), event.totalPrice());
        processedEvents.save(new ProcessedEvent(event.eventId(), "OrderPlaced", event.orderId()));
    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (alreadyProcessed(event.eventId())) {
            return;
        }
        log.info("Notification: payment for order {} completed (amount {})",
                event.orderId(), event.amount());
        processedEvents.save(new ProcessedEvent(event.eventId(), "PaymentCompleted", event.orderId()));
    }

    private boolean alreadyProcessed(String eventId) {
        if (processedEvents.existsById(eventId)) {
            log.info("Duplicate event {} — already processed, skipping", eventId);
            return true;
        }
        return false;
    }
}
