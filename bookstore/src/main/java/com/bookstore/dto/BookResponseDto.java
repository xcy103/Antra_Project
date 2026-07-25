package com.bookstore.dto;

import java.math.BigDecimal;

/**
 * Outbound representation of a book. Entities never leave the service layer;
 * controllers only ever see DTOs.
 */
public record BookResponseDto(
        Long id,
        String title,
        String isbn,
        BigDecimal price,
        Integer stock
) {
}
