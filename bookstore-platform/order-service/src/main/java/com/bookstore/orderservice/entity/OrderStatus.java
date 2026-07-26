package com.bookstore.orderservice.entity;

/**
 * Lifecycle of an order.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    CANCELLED,
    SHIPPED
}
