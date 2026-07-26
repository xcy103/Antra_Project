package com.bookstore.orderservice.exception;

/**
 * Thrown when book-service cannot be reached (down, timing out, or the circuit is
 * open). Mapped to HTTP 503 so the client can retry, rather than hanging.
 */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message) {
        super(message);
    }
}
