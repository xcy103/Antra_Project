package com.bookstore.paymentservice.controller;

import com.bookstore.paymentservice.dto.PaymentRequest;
import com.bookstore.paymentservice.dto.PaymentResponse;
import com.bookstore.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment endpoints. Pay for an order (one payment per order) and read the status.
 * Identity comes from the propagated JWT; owner/ADMIN checks in the service.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@Valid @RequestBody PaymentRequest request,
                               Authentication authentication) {
        return paymentService.pay(authentication.getName(), request);
    }

    @GetMapping("/{orderId}")
    public PaymentResponse getStatus(@PathVariable Long orderId, Authentication authentication) {
        return paymentService.getByOrderId(orderId, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
