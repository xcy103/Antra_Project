package com.bookstore.apigateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-authentication behavior of the gateway (the Phase 8 DoD: unauthenticated
 * requests are rejected at the edge). Downstream services aren't running, so
 * routed-through requests come back 5xx — the assertions only care that the
 * edge did (or didn't) short-circuit with 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class EdgeAuthenticationIntegrationTest {

    private static final String SECRET = "dev-only-insecure-secret-change-me-please-0123456789abcdef";

    @Autowired
    private WebTestClient client;

    private String validToken() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("alice").claim("role", "USER")
                .issuedAt(new Date(now)).expiration(new Date(now + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void protectedRoute_withoutToken_isRejectedAtEdge() {
        client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoute_withInvalidToken_isRejectedAtEdge() {
        client.get().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void catalogWrite_withoutToken_isRejectedAtEdge() {
        client.post().uri("/api/books").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoute_withValidToken_passesEdge() {
        // Passes edge auth, then routing hits a down backend -> 5xx (not 401).
        client.get().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    void publicAuthRoute_withoutToken_passesEdge() {
        client.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    void publicCatalogRead_withoutToken_passesEdge() {
        client.get().uri("/api/books")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }
}
