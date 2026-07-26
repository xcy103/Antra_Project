package com.bookstore.common.exception;

/**
 * Thrown when creating/updating a resource would violate a uniqueness rule
 * (e.g. a duplicate ISBN or username). Mapped to HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
