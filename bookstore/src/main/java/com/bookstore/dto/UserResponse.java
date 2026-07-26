package com.bookstore.dto;

import com.bookstore.entity.Role;
import com.bookstore.entity.User;

/**
 * Outbound representation of a user. Never exposes the password hash.
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        Role role
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
