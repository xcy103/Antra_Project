package com.bookstore.exception;

/**
 * Thrown when creating/updating a resource would violate a uniqueness rule
 * (e.g. a duplicate ISBN). Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
