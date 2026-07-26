package com.bookstore.orderservice.exception;

/**
 * Thrown when a requested quantity exceeds the available stock. Mapped to HTTP 409.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
