package com.bookstore.bookservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Inbound payload for creating/updating a book.
 */
public record BookRequestDto(

        @NotBlank(message = "title must not be blank")
        String title,

        @NotBlank(message = "isbn must not be blank")
        String isbn,

        @NotNull(message = "price must not be null")
        @Positive(message = "price must be positive")
        BigDecimal price,

        @NotNull(message = "stock must not be null")
        @PositiveOrZero(message = "stock must not be negative")
        Integer stock,

        @NotNull(message = "authorId must not be null")
        @Positive(message = "authorId must be positive")
        Long authorId
) {
}
