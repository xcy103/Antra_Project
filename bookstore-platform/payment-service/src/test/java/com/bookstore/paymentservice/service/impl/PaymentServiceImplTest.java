package com.bookstore.paymentservice.service.impl;

import com.bookstore.common.exception.DuplicateResourceException;
import com.bookstore.common.exception.ResourceNotFoundException;
import com.bookstore.paymentservice.dto.PaymentRequest;
import com.bookstore.paymentservice.dto.PaymentResponse;
import com.bookstore.paymentservice.entity.Payment;
import com.bookstore.paymentservice.entity.PaymentStatus;
import com.bookstore.paymentservice.exception.ForbiddenPaymentAccessException;
import com.bookstore.paymentservice.messaging.PaymentEventPublisher;
import com.bookstore.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private PaymentRequest request() {
        return new PaymentRequest(10L, new BigDecimal("29.99"));
    }

    @Test
    void pay_createsSuccessfulPayment() {
        when(paymentRepository.existsByOrderId(10L)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = paymentService.pay("alice", request());

        assertThat(result.orderId()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.amount()).isEqualByComparingTo("29.99");
    }

    @Test
    void pay_orderAlreadyPaid_throwsConflict() {
        when(paymentRepository.existsByOrderId(10L)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.pay("alice", request()))
                .isInstanceOf(DuplicateResourceException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void getByOrderId_missing_throwsNotFound() {
        when(paymentRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByOrderId(99L, "alice", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByOrderId_nonOwnerNonAdmin_forbidden() {
        Payment payment = new Payment(10L, "alice", new BigDecimal("29.99"), PaymentStatus.SUCCESS);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getByOrderId(10L, "bob", false))
                .isInstanceOf(ForbiddenPaymentAccessException.class);
    }

    @Test
    void getByOrderId_admin_canAccess() {
        Payment payment = new Payment(10L, "alice", new BigDecimal("29.99"), PaymentStatus.SUCCESS);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));

        PaymentResponse result = paymentService.getByOrderId(10L, "bob", true);

        assertThat(result.orderId()).isEqualTo(10L);
    }
}
