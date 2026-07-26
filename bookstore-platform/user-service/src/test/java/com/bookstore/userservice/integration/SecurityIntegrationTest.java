package com.bookstore.userservice.integration;

import com.bookstore.common.security.JwtUtil;
import com.bookstore.common.security.Role;
import com.bookstore.userservice.dto.AuthResponse;
import com.bookstore.userservice.dto.LoginRequest;
import com.bookstore.userservice.dto.RegisterRequest;
import com.bookstore.userservice.entity.User;
import com.bookstore.userservice.repository.UserRepository;
import com.bookstore.userservice.support.AbstractPostgresIT;
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
 * Security behavior of the standalone user-service: register/login/me, and the
 * 401/403/expired/bad-credentials/hash cases.
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

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String registerAndLogin(String username) {
        rest.postForEntity("/api/auth/register",
                new RegisterRequest(username, username + "@example.com", "password123"), Void.class);
        ResponseEntity<AuthResponse> login = rest.postForEntity("/api/auth/login",
                new LoginRequest(username, "password123"), AuthResponse.class);
        return login.getBody().token();
    }

    @Test
    void register_login_thenAccessMe() {
        String token = registerAndLogin("alice");

        ResponseEntity<String> me = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("alice");
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() {
        assertThat(rest.getForEntity("/api/users/me", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userHittingAdminEndpoint_returns403() {
        String userToken = registerAndLogin("bob");

        ResponseEntity<String> response = rest.exchange("/api/users", HttpMethod.GET,
                new HttpEntity<>(bearer(userToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminHittingAdminEndpoint_returns200() {
        userRepository.save(new User("root", "root@example.com",
                passwordEncoder.encode("password123"), Role.ADMIN));
        ResponseEntity<AuthResponse> login = rest.postForEntity("/api/auth/login",
                new LoginRequest("root", "password123"), AuthResponse.class);

        ResponseEntity<String> response = rest.exchange("/api/users", HttpMethod.GET,
                new HttpEntity<>(bearer(login.getBody().token())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expiredToken_returns401() {
        String expired = jwtUtil.generateToken("alice", Role.USER, -1000L);

        ResponseEntity<String> response = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(bearer(expired)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void badCredentials_returns401() {
        rest.postForEntity("/api/auth/register",
                new RegisterRequest("carol", "carol@example.com", "password123"), Void.class);

        ResponseEntity<String> response = rest.postForEntity("/api/auth/login",
                new LoginRequest("carol", "wrong-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void password_isStoredAsBcryptHash() {
        rest.postForEntity("/api/auth/register",
                new RegisterRequest("dave", "dave@example.com", "password123"), Void.class);

        User saved = userRepository.findByUsername("dave").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
    }
}
