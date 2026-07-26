package com.bookstore.controller;

import com.bookstore.dto.AuthResponse;
import com.bookstore.dto.LoginRequest;
import com.bookstore.dto.RegisterRequest;
import com.bookstore.dto.UserResponse;
import com.bookstore.entity.Role;
import com.bookstore.exception.DuplicateResourceException;
import com.bookstore.security.JwtUtil;
import com.bookstore.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the auth endpoints: validation, and how service exceptions
 * map to HTTP status (duplicate -> 409, bad credentials -> 401).
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    // Satisfies the JwtAuthenticationFilter bean created by the web slice.
    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void register_valid_returns201() throws Exception {
        when(authService.register(any()))
                .thenReturn(new UserResponse(1L, "alice", "alice@example.com", Role.USER));
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("alice", "alice@example.com", "password123"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void register_invalidPayload_returns400() throws Exception {
        // blank username, invalid email, too-short password
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("", "not-an-email", "short"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new DuplicateResourceException("Username already taken: alice"));
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("alice", "alice@example.com", "password123"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void login_valid_returnsToken() throws Exception {
        when(authService.login(any())).thenReturn(AuthResponse.bearer("jwt-token", 3600));
        String body = objectMapper.writeValueAsString(new LoginRequest("alice", "password123"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));
        String body = objectMapper.writeValueAsString(new LoginRequest("alice", "wrong"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
