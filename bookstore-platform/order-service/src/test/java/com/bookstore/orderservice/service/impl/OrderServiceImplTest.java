package com.bookstore.orderservice.service.impl;

import com.bookstore.orderservice.client.BookClient;
import com.bookstore.orderservice.client.BookDto;
import com.bookstore.orderservice.dto.OrderItemRequest;
import com.bookstore.orderservice.dto.OrderResponse;
import com.bookstore.orderservice.dto.PlaceOrderRequest;
import com.bookstore.orderservice.entity.Order;
import com.bookstore.orderservice.entity.OrderItem;
import com.bookstore.orderservice.entity.OrderStatus;
import com.bookstore.orderservice.exception.CatalogUnavailableException;
import com.bookstore.orderservice.exception.ForbiddenOrderAccessException;
import com.bookstore.orderservice.exception.InsufficientStockException;
import com.bookstore.orderservice.exception.OrderStateException;
import com.bookstore.orderservice.messaging.OrderEventPublisher;
import com.bookstore.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private BookClient bookClient;
    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private PlaceOrderRequest orderFor(Long bookId, int quantity) {
        return new PlaceOrderRequest(List.of(new OrderItemRequest(bookId, quantity)));
    }

    @Test
    void placeOrder_snapshotsPriceAndComputesTotal() {
        when(bookClient.getBook(1L)).thenReturn(new BookDto(1L, new BigDecimal("10.00"), 5));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = orderService.placeOrder("alice", orderFor(1L, 2));

        assertThat(result.ownerUsername()).isEqualTo("alice");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.totalPrice()).isEqualByComparingTo("20.00");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).unitPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void placeOrder_insufficientStock_throws() {
        when(bookClient.getBook(1L)).thenReturn(new BookDto(1L, new BigDecimal("10.00"), 1));

        assertThatThrownBy(() -> orderService.placeOrder("alice", orderFor(1L, 5)))
                .isInstanceOf(InsufficientStockException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_catalogUnavailable_propagates() {
        // Simulates the Feign fallback firing when book-service is down.
        when(bookClient.getBook(1L)).thenThrow(new CatalogUnavailableException("down"));

        assertThatThrownBy(() -> orderService.placeOrder("alice", orderFor(1L, 1)))
                .isInstanceOf(CatalogUnavailableException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrder_nonOwnerNonAdmin_forbidden() {
        Order order = new Order("alice");
        order.addItem(new OrderItem(1L, 1, new BigDecimal("10.00")));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(1L, "bob", false))
                .isInstanceOf(ForbiddenOrderAccessException.class);
    }

    @Test
    void getOrder_admin_canAccessOthersOrder() {
        Order order = new Order("alice");
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrder(1L, "bob", true);

        assertThat(result.ownerUsername()).isEqualTo("alice");
    }

    @Test
    void cancelOrder_shipped_throwsOrderState() {
        Order order = new Order("alice");
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "alice", false))
                .isInstanceOf(OrderStateException.class);
    }

    @Test
    void cancelOrder_owner_setsCancelled() {
        Order order = new Order("alice");
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = orderService.cancelOrder(1L, "alice", false);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
    }
}
