package com.bookstore.orderservice.integration;

import com.bookstore.common.security.JwtUtil;
import com.bookstore.common.security.Role;
import com.bookstore.orderservice.dto.OrderItemRequest;
import com.bookstore.orderservice.dto.PlaceOrderRequest;
import com.bookstore.orderservice.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The core Phase 5 acceptance, automated: with book-service unreachable, placing
 * an order must <em>degrade gracefully</em> (fast 503 via the Feign fallback)
 * rather than hang or cascade timeouts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderResilienceIntegrationTest extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JwtUtil jwtUtil;

    @DynamicPropertySource
    static void bookServiceDown(DynamicPropertyRegistry registry) {
        // Point at an unreachable address so the Feign call fails immediately.
        registry.add("book-service.url", () -> "http://localhost:1");
    }

    @Test
    void placeOrder_whenBookServiceDown_returns503NotHang() {
        String userToken = jwtUtil.generateToken("alice", Role.USER);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        PlaceOrderRequest body = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 1)));

        long start = System.currentTimeMillis();
        ResponseEntity<String> response = rest.exchange("/api/orders", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // Graceful: it fails fast rather than hanging on a long timeout.
        assertThat(elapsedMs).isLessThan(10_000);
    }

    @Test
    void placeOrder_withoutToken_returns401() {
        PlaceOrderRequest body = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 1)));
        ResponseEntity<String> response = rest.exchange("/api/orders", HttpMethod.POST,
                new HttpEntity<>(body, new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
