package com.bookstore.analyticsservice.service;

import com.bookstore.analyticsservice.entity.Metric;
import com.bookstore.analyticsservice.entity.ProcessedEvent;
import com.bookstore.analyticsservice.repository.MetricRepository;
import com.bookstore.analyticsservice.repository.ProcessedEventRepository;
import com.bookstore.common.event.OrderPlacedEvent;
import com.bookstore.common.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates order/payment metrics idempotently: the {@code processed_event}
 * ledger guards against double-counting on redelivery, so each event bumps the
 * running totals exactly once.
 */
@Service
public class AnalyticsHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsHandler.class);

    private final ProcessedEventRepository processedEvents;
    private final MetricRepository metrics;

    public AnalyticsHandler(ProcessedEventRepository processedEvents, MetricRepository metrics) {
        this.processedEvents = processedEvents;
        this.metrics = metrics;
    }

    @Transactional
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (alreadyProcessed(event.eventId())) {
            return;
        }
        record("orders", event.totalPrice());
        processedEvents.save(new ProcessedEvent(event.eventId(), "OrderPlaced"));
        log.info("Analytics: counted order {} (revenue += {})", event.orderId(), event.totalPrice());
    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (alreadyProcessed(event.eventId())) {
            return;
        }
        record("payments", event.amount());
        processedEvents.save(new ProcessedEvent(event.eventId(), "PaymentCompleted"));
        log.info("Analytics: counted payment for order {} (amount += {})", event.orderId(), event.amount());
    }

    private void record(String metricName, java.math.BigDecimal amount) {
        Metric metric = metrics.findById(metricName).orElseGet(() -> new Metric(metricName));
        metric.add(amount);
        metrics.save(metric);
    }

    private boolean alreadyProcessed(String eventId) {
        if (processedEvents.existsById(eventId)) {
            log.info("Duplicate event {} — already counted, skipping", eventId);
            return true;
        }
        return false;
    }
}
