package com.bookstore.paymentservice.service;

import com.bookstore.paymentservice.dto.PaymentRequest;
import com.bookstore.paymentservice.dto.PaymentResponse;

public interface PaymentService {

    PaymentResponse pay(String owner, PaymentRequest request);

    PaymentResponse getByOrderId(Long orderId, String requester, boolean isAdmin);
}
