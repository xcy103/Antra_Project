package com.bookstore.paymentservice.exception;

import com.bookstore.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Payment-specific exception mapping (forbidden access → 403), on top of the
 * common handler.
 */
@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(ForbiddenPaymentAccessException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenPaymentAccessException ex,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase(),
                ex.getMessage(), request.getRequestURI()));
    }
}
