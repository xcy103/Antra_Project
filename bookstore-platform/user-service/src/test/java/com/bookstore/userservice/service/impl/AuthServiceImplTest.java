package com.bookstore.userservice.service.impl;

import com.bookstore.common.exception.DuplicateResourceException;
import com.bookstore.common.security.JwtUtil;
import com.bookstore.common.security.Role;
import com.bookstore.userservice.dto.AuthResponse;
import com.bookstore.userservice.dto.LoginRequest;
import com.bookstore.userservice.dto.RegisterRequest;
import com.bookstore.userservice.dto.UserResponse;
import com.bookstore.userservice.entity.User;
import com.bookstore.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest() {
        return new RegisterRequest("alice", "alice@example.com", "password123");
    }

    @Test
    void register_hashesPasswordAndCreatesUser() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        UserResponse result = authService.register(request);

        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.role()).isEqualTo(Role.USER);
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Username");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email");
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_authenticatesAndIssuesToken() {
        User user = new User("alice", "alice@example.com", "bcrypt-hash", Role.USER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("alice", Role.USER)).thenReturn("jwt-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);

        AuthResponse result = authService.login(new LoginRequest("alice", "password123"));

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}
