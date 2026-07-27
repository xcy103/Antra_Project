package com.bookstore.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayJwtValidatorTest {

    private static final String SECRET = "dev-only-insecure-secret-change-me-please-0123456789abcdef";

    private final GatewayJwtValidator validator = new GatewayJwtValidator(SECRET);

    private String token(String secret, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("alice")
                .claim("role", "USER")
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void parsesValidToken() {
        Claims claims = validator.parse(token(SECRET, 3_600_000L));

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void rejectsExpiredToken() {
        assertThatThrownBy(() -> validator.parse(token(SECRET, -1_000L)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongSignature() {
        String forged = token("a-different-secret-a-different-secret-0123456789", 3_600_000L);
        assertThatThrownBy(() -> validator.parse(forged))
                .isInstanceOf(JwtException.class);
    }
}
