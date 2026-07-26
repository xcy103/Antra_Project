package com.bookstore.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. {@code toString} redacts the password so it never reaches
 * logs (the service layer is logged by the common LoggingAspect).
 */
public record RegisterRequest(

        @NotBlank(message = "username must not be blank")
        @Size(min = 3, max = 50, message = "username must be 3-50 characters")
        String username,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be valid")
        String email,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 100, message = "password must be 8-100 characters")
        String password
) {
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + ", password=***]";
    }
}
