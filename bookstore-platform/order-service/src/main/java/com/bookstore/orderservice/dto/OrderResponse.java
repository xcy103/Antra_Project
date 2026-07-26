package com.bookstore.orderservice.dto;

import com.bookstore.orderservice.entity.Order;
import com.bookstore.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String ownerUsername,
        OrderStatus status,
        BigDecimal totalPrice,
        Instant createdAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getBookId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(order.getId(), order.getOwnerUsername(), order.getStatus(),
                order.getTotalPrice(), order.getCreatedAt(), items);
    }
}
