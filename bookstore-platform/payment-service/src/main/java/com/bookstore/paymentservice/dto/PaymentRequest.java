package com.bookstore.paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentRequest(

        @NotNull(message = "orderId must not be null")
        @Positive(message = "orderId must be positive")
        Long orderId,

        @NotNull(message = "amount must not be null")
        @Positive(message = "amount must be positive")
        BigDecimal amount
) {
}
