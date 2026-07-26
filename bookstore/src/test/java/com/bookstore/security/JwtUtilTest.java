package com.bookstore.security;

import com.bookstore.entity.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3_600_000L);

    @Test
    void validToken_roundTripsSubjectAndRole() {
        String token = jwtUtil.generateToken("alice", Role.USER);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("USER");
    }

    @Test
    void expiredToken_isRejected() {
        String expired = jwtUtil.generateToken("bob", Role.ADMIN, -1_000L);

        assertThatThrownBy(() -> jwtUtil.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        JwtUtil other = new JwtUtil("a-completely-different-secret-abcdefghijklmnop", 3_600_000L);
        String token = other.generateToken("alice", Role.USER);

        assertThatThrownBy(() -> jwtUtil.parse(token))
                .isInstanceOf(JwtException.class);
    }
}
