package com.bookstore.paymentservice.exception;

/**
 * Thrown when a non-owner (and non-ADMIN) tries to read a payment. Mapped to 403.
 */
public class ForbiddenPaymentAccessException extends RuntimeException {

    public ForbiddenPaymentAccessException(String message) {
        super(message);
    }
}
