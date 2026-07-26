package com.bookstore.userservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload. {@code toString} redacts the password so it never reaches logs.
 */
public record LoginRequest(

        @NotBlank(message = "username must not be blank")
        String username,

        @NotBlank(message = "password must not be blank")
        String password
) {
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
