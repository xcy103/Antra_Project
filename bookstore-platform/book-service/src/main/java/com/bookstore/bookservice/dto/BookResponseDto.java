package com.bookstore.bookservice.dto;

import java.math.BigDecimal;

/**
 * Outbound representation of a book, including a flattened author reference.
 * This is also what order-service consumes over Feign to snapshot price/stock.
 */
public record BookResponseDto(
        Long id,
        String title,
        String isbn,
        BigDecimal price,
        Integer stock,
        Long authorId,
        String authorName
) {
}
