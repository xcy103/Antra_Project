package com.bookstore.userservice.controller;

import com.bookstore.common.exception.DuplicateResourceException;
import com.bookstore.common.security.JwtUtil;
import com.bookstore.common.security.Role;
import com.bookstore.userservice.dto.AuthResponse;
import com.bookstore.userservice.dto.LoginRequest;
import com.bookstore.userservice.dto.RegisterRequest;
import com.bookstore.userservice.dto.UserResponse;
import com.bookstore.userservice.service.AuthService;
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

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;
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
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void register_invalidPayload_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("", "not-an-email", "short"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new DuplicateResourceException("Username already taken: alice"));
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("alice", "alice@example.com", "password123"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void login_valid_returnsToken() throws Exception {
        when(authService.login(any())).thenReturn(AuthResponse.bearer("jwt-token", 3600));
        String body = objectMapper.writeValueAsString(new LoginRequest("alice", "password123"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));
        String body = objectMapper.writeValueAsString(new LoginRequest("alice", "wrong"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}
