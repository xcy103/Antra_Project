package com.bookstore.security;

import com.bookstore.dto.AuthResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end security tests against a real PostgreSQL: register/login, token
 * access, 401 without a token, 403 for a USER on an ADMIN endpoint, rejection of
 * an expired token, and confirmation that passwords are stored as BCrypt hashes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    private String login(String username, String password) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest(username, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    void register_login_thenAccessMe() {
        RegisterRequest register = new RegisterRequest("alice", "alice@example.com", "password123");
        ResponseEntity<UserResponse> registered =
                rest.postForEntity("/api/auth/register", register, UserResponse.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody().role()).isEqualTo(Role.USER);

        String token = login("alice", "password123");

        ResponseEntity<UserResponse> me = rest.exchange(
                "/api/users/me", HttpMethod.GET, bearer(token), UserResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().username()).isEqualTo("alice");
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() {
        ResponseEntity<String> response = rest.getForEntity("/api/users/me", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userHittingAdminEndpoint_returns403() {
        rest.postForEntity("/api/auth/register",
                new RegisterRequest("bob", "bob@example.com", "password123"), UserResponse.class);
        String token = login("bob", "password123");

        ResponseEntity<String> response = rest.exchange(
                "/api/users", HttpMethod.GET, bearer(token), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminHittingAdminEndpoint_returns200() {
        userRepository.save(new User("admin", "admin@example.com",
                passwordEncoder.encode("password123"), Role.ADMIN));
        String token = login("admin", "password123");

        ResponseEntity<UserResponse[]> response = rest.exchange(
                "/api/users", HttpMethod.GET, bearer(token), UserResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(UserResponse::username).contains("admin");
    }

    @Test
    void expiredToken_returns401() {
        String expired = jwtUtil.generateToken("ghost", Role.USER, -1_000L);

        ResponseEntity<String> response = rest.exchange(
                "/api/users/me", HttpMethod.GET, bearer(expired), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void badCredentials_returns401() {
        rest.postForEntity("/api/auth/register",
                new RegisterRequest("carol", "carol@example.com", "password123"), UserResponse.class);

        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest("carol", "wrong-password"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void password_isStoredAsBcryptHash() {
        rest.postForEntity("/api/auth/register",
                new RegisterRequest("dave", "dave@example.com", "password123"), UserResponse.class);

        User stored = userRepository.findByUsername("dave").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("password123");
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("password123", stored.getPasswordHash())).isTrue();
    }
}
