package com.bookstore.orderservice.service;

import com.bookstore.common.event.PaymentCompletedEvent;
import com.bookstore.orderservice.entity.Order;
import com.bookstore.orderservice.entity.OrderStatus;
import com.bookstore.orderservice.entity.ProcessedEvent;
import com.bookstore.orderservice.repository.OrderRepository;
import com.bookstore.orderservice.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies {@link PaymentCompletedEvent}s to orders idempotently: on the first
 * receipt of an event id, a PENDING order is moved to PAID. The
 * {@code processed_event} ledger (keyed by event id) makes Kafka's at-least-once
 * redelivery safe — a duplicate is recognized and skipped. Orders that are no
 * longer PENDING (e.g. already CANCELLED) are left untouched; the event is still
 * recorded so it is not reprocessed.
 */
@Service
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEvents;

    public PaymentEventHandler(OrderRepository orderRepository, ProcessedEventRepository processedEvents) {
        this.orderRepository = orderRepository;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Duplicate event {} — already processed, skipping", event.eventId());
            return;
        }

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("PaymentCompleted for unknown order {} — recording event, no status change",
                    event.orderId());
        } else if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            log.info("Order {} marked PAID (payment event {})", order.getId(), event.eventId());
        } else {
            log.info("Order {} is {} (not PENDING) — leaving status unchanged",
                    order.getId(), order.getStatus());
        }

        processedEvents.save(new ProcessedEvent(event.eventId(), "PaymentCompleted", event.orderId()));
    }
}
