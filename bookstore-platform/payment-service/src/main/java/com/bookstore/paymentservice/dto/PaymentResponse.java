package com.bookstore.paymentservice.dto;

import com.bookstore.paymentservice.entity.Payment;
import com.bookstore.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        Instant paidAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getStatus(), payment.getPaidAt());
    }
}
