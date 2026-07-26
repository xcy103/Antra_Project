package com.bookstore.security;

import com.bookstore.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies HS256-signed JWTs. The token carries the username as the
 * subject and the role as a claim; the server trusts these after verifying the
 * signature and expiry, so no session/DB lookup is needed per request.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms}") long expirationMillis) {
        // HS256 requires a key of at least 256 bits (32 bytes).
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(String username, Role role) {
        return generateToken(username, role, expirationMillis);
    }

    /** Explicit TTL overload — used by tests to produce an already-expired token. */
    public String generateToken(String username, Role role, long ttlMillis) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(key)
                .compact();
    }

    /** Verifies signature + expiry; throws {@link io.jsonwebtoken.JwtException} if invalid. */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    public String extractUsername(String token) {
        return parse(token).getPayload().getSubject();
    }

    public String extractRole(String token) {
        return parse(token).getPayload().get("role", String.class);
    }

    public long getExpirationSeconds() {
        return expirationMillis / 1000;
    }
}
