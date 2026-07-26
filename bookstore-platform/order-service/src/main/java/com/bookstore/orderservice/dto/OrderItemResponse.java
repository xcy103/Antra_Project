package com.bookstore.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long bookId,
        Integer quantity,
        BigDecimal unitPrice
) {
}
