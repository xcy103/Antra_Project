package com.bookstore.integration;

import com.bookstore.dto.AuthResponse;
import com.bookstore.dto.BookRequestDto;
import com.bookstore.dto.BookResponseDto;
import com.bookstore.dto.LoginRequest;
import com.bookstore.dto.RegisterRequest;
import com.bookstore.dto.UserResponse;
import com.bookstore.entity.Role;
import com.bookstore.entity.User;
import com.bookstore.repository.UserRepository;
import com.bookstore.support.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end happy path across the whole stack (real HTTP + real PostgreSQL):
 * register a user, log in, have an ADMIN create a book, then read it back
 * publicly — and confirm a plain USER cannot create a book.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndIntegrationTest extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void register_login_adminCreatesBook_thenQueryPublicly() {
        // 1. Register a normal USER.
        ResponseEntity<UserResponse> register = rest.postForEntity("/api/auth/register",
                new RegisterRequest("e2e_user", "e2e_user@example.com", "password123"), UserResponse.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Log in as that USER.
        String userToken = login("e2e_user", "password123");

        // 3. Provision an ADMIN out of band, then log in as ADMIN.
        userRepository.save(new User("e2e_admin", "e2e_admin@example.com",
                passwordEncoder.encode("adminpass1"), Role.ADMIN));
        String adminToken = login("e2e_admin", "adminpass1");

        // 4. ADMIN creates a book (authorId 1 is seeded by V2).
        BookRequestDto newBook = new BookRequestDto("E2E Title", "e2e-isbn-001", new BigDecimal("29.99"), 7, 1L);
        ResponseEntity<BookResponseDto> created = rest.exchange("/api/books", HttpMethod.POST,
                new HttpEntity<>(newBook, bearer(adminToken)), BookResponseDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long bookId = created.getBody().id();
        assertThat(bookId).isNotNull();

        // 5. Anyone can read the book (public GET).
        ResponseEntity<BookResponseDto> fetched = rest.getForEntity("/api/books/" + bookId, BookResponseDto.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().title()).isEqualTo("E2E Title");
        assertThat(fetched.getBody().authorName()).isNotBlank();

        // 6. A plain USER may NOT create a book.
        ResponseEntity<String> forbidden = rest.exchange("/api/books", HttpMethod.POST,
                new HttpEntity<>(newBook, bearer(userToken)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String login(String username, String password) {
        ResponseEntity<AuthResponse> response = rest.postForEntity("/api/auth/login",
                new LoginRequest(username, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
