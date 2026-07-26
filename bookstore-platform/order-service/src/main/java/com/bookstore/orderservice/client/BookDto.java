package com.bookstore.orderservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Subset of book-service's book representation that order-service needs to snapshot
 * price and check stock. Unknown fields (title, isbn, author…) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookDto(
        Long id,
        BigDecimal price,
        Integer stock
) {
}
