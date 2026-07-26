package com.bookstore.paymentservice.integration;

import com.bookstore.common.security.JwtUtil;
import com.bookstore.common.security.Role;
import com.bookstore.paymentservice.dto.PaymentRequest;
import com.bookstore.paymentservice.messaging.PaymentEventPublisher;
import com.bookstore.paymentservice.repository.PaymentRepository;
import com.bookstore.paymentservice.support.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment-service behavior over real HTTP + PostgreSQL: authenticated pay,
 * one-payment-per-order, unauthenticated rejection, and status retrieval.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentIntegrationTest extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private JwtUtil jwtUtil;

    // No broker in this test; the Kafka round-trip is covered by the consumer
    // services' Testcontainers tests. Mock the publisher so pay() doesn't block.
    @MockitoBean
    private PaymentEventPublisher paymentEventPublisher;

    @BeforeEach
    void clean() {
        paymentRepository.deleteAll();
    }

    private HttpHeaders bearer(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generateToken(username, Role.USER));
        return headers;
    }

    @Test
    void pay_thenReadStatus() {
        PaymentRequest body = new PaymentRequest(100L, new BigDecimal("29.99"));

        ResponseEntity<String> pay = rest.exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(body, bearer("alice")), String.class);
        assertThat(pay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(pay.getBody()).contains("SUCCESS");

        ResponseEntity<String> status = rest.exchange("/api/payments/100", HttpMethod.GET,
                new HttpEntity<>(bearer("alice")), String.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void pay_withoutToken_returns401() {
        PaymentRequest body = new PaymentRequest(101L, new BigDecimal("10.00"));
        ResponseEntity<String> response = rest.exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(body, new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void payingSameOrderTwice_returns409() {
        PaymentRequest body = new PaymentRequest(102L, new BigDecimal("15.00"));
        rest.exchange("/api/payments", HttpMethod.POST, new HttpEntity<>(body, bearer("alice")), String.class);

        ResponseEntity<String> second = rest.exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(body, bearer("alice")), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
