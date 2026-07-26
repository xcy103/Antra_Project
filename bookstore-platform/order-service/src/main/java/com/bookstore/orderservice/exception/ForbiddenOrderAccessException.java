package com.bookstore.orderservice.exception;

/**
 * Thrown when a non-owner (and non-ADMIN) tries to view or cancel an order.
 * Mapped to HTTP 403.
 */
public class ForbiddenOrderAccessException extends RuntimeException {

    public ForbiddenOrderAccessException(String message) {
        super(message);
    }
}
