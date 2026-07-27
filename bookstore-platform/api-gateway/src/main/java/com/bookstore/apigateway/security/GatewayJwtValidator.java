package com.bookstore.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Verifies the HS256 JWT at the edge using the shared secret. Parse-only — the
 * gateway never issues tokens (user-service does). Kept gateway-local so the
 * reactive gateway doesn't have to depend on the servlet-based common module.
 */
@Component
public class GatewayJwtValidator {

    private final SecretKey key;

    public GatewayJwtValidator(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Verifies signature + expiry; throws {@link io.jsonwebtoken.JwtException} if invalid. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
