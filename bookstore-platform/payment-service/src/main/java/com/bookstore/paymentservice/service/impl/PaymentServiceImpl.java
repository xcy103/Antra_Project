package com.bookstore.paymentservice.service.impl;

import com.bookstore.common.exception.DuplicateResourceException;
import com.bookstore.common.exception.ResourceNotFoundException;
import com.bookstore.paymentservice.dto.PaymentRequest;
import com.bookstore.paymentservice.dto.PaymentResponse;
import com.bookstore.paymentservice.entity.Payment;
import com.bookstore.paymentservice.entity.PaymentStatus;
import com.bookstore.paymentservice.exception.ForbiddenPaymentAccessException;
import com.bookstore.paymentservice.repository.PaymentRepository;
import com.bookstore.paymentservice.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public PaymentResponse pay(String owner, PaymentRequest request) {
        // One payment per order (idempotency guard; DB also enforces the UNIQUE).
        if (paymentRepository.existsByOrderId(request.orderId())) {
            throw new DuplicateResourceException("Order already paid: " + request.orderId());
        }
        // Payment is simulated as successful. In Phase 7 a PaymentCompleted event is
        // published so order-service can mark the order PAID.
        Payment payment = new Payment(request.orderId(), owner, request.amount(), PaymentStatus.SUCCESS);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(Long orderId, String requester, boolean isAdmin) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment for order " + orderId));
        if (!isAdmin && !payment.getOwnerUsername().equals(requester)) {
            throw new ForbiddenPaymentAccessException("You do not have access to this payment");
        }
        return PaymentResponse.from(payment);
    }
}
