package com.bookstore.bookservice.integration;

import com.bookstore.bookservice.dto.BookRequestDto;
import com.bookstore.bookservice.support.AbstractPostgresIT;
import com.bookstore.common.security.JwtUtil;
import com.bookstore.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies book-service validates propagated JWTs and enforces roles: catalog
 * reads are public, writes require an ADMIN token minted with the shared secret.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookSecurityIntegrationTest extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JwtUtil jwtUtil;

    private BookRequestDto newBook(String isbn) {
        return new BookRequestDto("Secured Title", isbn, new BigDecimal("29.99"), 3, 1L);
    }

    private HttpEntity<BookRequestDto> withToken(BookRequestDto body, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    @Test
    void catalogRead_isPublic() {
        assertThat(rest.getForEntity("/api/books", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void createWithoutToken_returns401() {
        ResponseEntity<String> response = rest.exchange("/api/books", HttpMethod.POST,
                withToken(newBook("sec-401"), null), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createWithUserToken_returns403() {
        String userToken = jwtUtil.generateToken("alice", Role.USER);
        ResponseEntity<String> response = rest.exchange("/api/books", HttpMethod.POST,
                withToken(newBook("sec-403"), userToken), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createWithAdminToken_returns201() {
        String adminToken = jwtUtil.generateToken("admin", Role.ADMIN);
        ResponseEntity<String> response = rest.exchange("/api/books", HttpMethod.POST,
                withToken(newBook("sec-201"), adminToken), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
