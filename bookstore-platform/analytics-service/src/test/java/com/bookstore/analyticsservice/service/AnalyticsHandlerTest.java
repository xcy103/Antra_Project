package com.bookstore.analyticsservice.service;

import com.bookstore.analyticsservice.entity.Metric;
import com.bookstore.analyticsservice.repository.MetricRepository;
import com.bookstore.analyticsservice.repository.ProcessedEventRepository;
import com.bookstore.common.event.OrderPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class AnalyticsHandlerTest {

    @Mock
    private ProcessedEventRepository processedEvents;
    @Mock
    private MetricRepository metrics;

    @InjectMocks
    private AnalyticsHandler handler;

    private OrderPlacedEvent orderEvent(String eventId) {
        return new OrderPlacedEvent(eventId, 7L, "alice", new BigDecimal("12.50"), Instant.now());
    }

    @Test
    void firstOrderPlaced_incrementsOrdersMetric() {
        when(processedEvents.existsById("e1")).thenReturn(false);
        when(metrics.findById("orders")).thenReturn(Optional.empty());

        handler.handleOrderPlaced(orderEvent("e1"));

        ArgumentCaptor<Metric> captor = ArgumentCaptor.forClass(Metric.class);
        verify(metrics).save(captor.capture());
        assertThat(captor.getValue().getTotalCount()).isEqualTo(1L);
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("12.50");
        verify(processedEvents).save(any());
    }

    @Test
    void duplicateOrderPlaced_doesNotCount() {
        when(processedEvents.existsById("e1")).thenReturn(true);

        handler.handleOrderPlaced(orderEvent("e1"));

        verify(metrics, never()).save(any());
        verify(processedEvents, never()).save(any());
    }
}
