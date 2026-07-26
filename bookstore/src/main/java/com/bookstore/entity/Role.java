package com.bookstore.entity;

/**
 * Application roles. Stored as a string in the DB and surfaced to Spring Security
 * as authority {@code ROLE_USER} / {@code ROLE_ADMIN}.
 */
public enum Role {
    USER,
    ADMIN
}
