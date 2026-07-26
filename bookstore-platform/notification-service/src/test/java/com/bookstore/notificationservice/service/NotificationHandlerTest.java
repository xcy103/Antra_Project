package com.bookstore.notificationservice.service;

import com.bookstore.common.event.OrderPlacedEvent;
import com.bookstore.common.event.PaymentCompletedEvent;
import com.bookstore.notificationservice.entity.ProcessedEvent;
import com.bookstore.notificationservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationHandlerTest {

    @Mock
    private ProcessedEventRepository processedEvents;

    @InjectMocks
    private NotificationHandler handler;

    private OrderPlacedEvent orderEvent(String eventId) {
        return new OrderPlacedEvent(eventId, 7L, "alice", new BigDecimal("10.00"), Instant.now());
    }

    @Test
    void firstOrderPlaced_isProcessedAndRecorded() {
        when(processedEvents.existsById("e1")).thenReturn(false);

        handler.handleOrderPlaced(orderEvent("e1"));

        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void duplicateOrderPlaced_isSkipped() {
        when(processedEvents.existsById("e1")).thenReturn(true);

        handler.handleOrderPlaced(orderEvent("e1"));

        verify(processedEvents, never()).save(any());
    }

    @Test
    void duplicatePaymentCompleted_isSkipped() {
        when(processedEvents.existsById("p1")).thenReturn(true);

        handler.handlePaymentCompleted(new PaymentCompletedEvent("p1", 7L, new BigDecimal("10.00"), Instant.now()));

        verify(processedEvents, never()).save(any());
    }
}
