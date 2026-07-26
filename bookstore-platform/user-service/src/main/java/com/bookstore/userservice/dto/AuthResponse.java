package com.bookstore.userservice.dto;

/**
 * Login response: the signed JWT and how long it is valid for (seconds).
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds
) {
    public static AuthResponse bearer(String token, long expiresInSeconds) {
        return new AuthResponse(token, "Bearer", expiresInSeconds);
    }
}
