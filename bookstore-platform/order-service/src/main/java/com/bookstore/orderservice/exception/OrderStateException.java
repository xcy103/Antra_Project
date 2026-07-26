package com.bookstore.orderservice.exception;

/**
 * Thrown when an operation is invalid for the order's current state
 * (e.g. cancelling an already-shipped order). Mapped to HTTP 409.
 */
public class OrderStateException extends RuntimeException {

    public OrderStateException(String message) {
        super(message);
    }
}
