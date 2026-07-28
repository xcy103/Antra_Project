package com.bookstore.orderservice.service;

import com.bookstore.common.event.PaymentCompletedEvent;
import com.bookstore.orderservice.entity.Order;
import com.bookstore.orderservice.entity.OrderStatus;
import com.bookstore.orderservice.entity.ProcessedEvent;
import com.bookstore.orderservice.repository.OrderRepository;
import com.bookstore.orderservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventHandlerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProcessedEventRepository processedEvents;
    @InjectMocks
    private PaymentEventHandler handler;

    private PaymentCompletedEvent event(String id, long orderId) {
        return new PaymentCompletedEvent(id, orderId, new BigDecimal("29.99"), Instant.now());
    }

    @Test
    void marksPendingOrderPaid_andRecordsEvent() {
        Order order = new Order("alice"); // starts PENDING
        when(processedEvents.existsById("e1")).thenReturn(false);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        handler.handlePaymentCompleted(event("e1", 10L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void duplicateEvent_isSkippedEntirely() {
        when(processedEvents.existsById("e1")).thenReturn(true);

        handler.handlePaymentCompleted(event("e1", 10L));

        verify(orderRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
        verify(processedEvents, never()).save(any());
    }

    @Test
    void unknownOrder_recordsEventWithoutStatusChange() {
        when(processedEvents.existsById("e2")).thenReturn(false);
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        handler.handlePaymentCompleted(event("e2", 99L));

        verify(orderRepository, never()).save(any());
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    @Test
    void nonPendingOrder_leavesStatusButStillRecordsEvent() {
        Order order = new Order("alice");
        order.setStatus(OrderStatus.CANCELLED);
        when(processedEvents.existsById("e3")).thenReturn(false);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        handler.handlePaymentCompleted(event("e3", 10L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, never()).save(any());
        verify(processedEvents).save(any(ProcessedEvent.class));
    }
}
