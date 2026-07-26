package com.bookstore.common.security;

/**
 * Application roles, shared across services. Surfaced to Spring Security as
 * authority {@code ROLE_USER} / {@code ROLE_ADMIN}.
 */
public enum Role {
    USER,
    ADMIN
}
