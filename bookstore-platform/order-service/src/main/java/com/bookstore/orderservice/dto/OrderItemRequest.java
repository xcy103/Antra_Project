package com.bookstore.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(

        @NotNull(message = "bookId must not be null")
        @Positive(message = "bookId must be positive")
        Long bookId,

        @NotNull(message = "quantity must not be null")
        @Positive(message = "quantity must be positive")
        Integer quantity
) {
}
