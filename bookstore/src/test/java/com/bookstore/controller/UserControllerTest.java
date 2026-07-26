package com.bookstore.controller;

import com.bookstore.dto.UserResponse;
import com.bookstore.entity.Role;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.security.JwtUtil;
import com.bookstore.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the user endpoints. Filters are off, so authorization
 * (ADMIN-only) is not exercised here (that lives in SecurityIntegrationTest);
 * these verify mapping and that {@code /me} reads the authenticated principal.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // Satisfies the JwtAuthenticationFilter bean created by the web slice.
    @MockitoBean
    private JwtUtil jwtUtil;

    private Authentication principal(String username) {
        return new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void me_returnsCurrentUser() throws Exception {
        when(userService.getByUsername("alice"))
                .thenReturn(new UserResponse(1L, "alice", "alice@example.com", Role.USER));

        mockMvc.perform(get("/api/users/me").principal(principal("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getAllUsers_returnsList() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(
                new UserResponse(1L, "alice", "alice@example.com", Role.USER),
                new UserResponse(2L, "admin", "admin@example.com", Role.ADMIN)));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[1].role").value("ADMIN"));
    }

    @Test
    void getUserById_returnsUser() throws Exception {
        when(userService.getById(1L))
                .thenReturn(new UserResponse(1L, "alice", "alice@example.com", Role.USER));

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        when(userService.getById(99L)).thenThrow(new ResourceNotFoundException("User not found with id 99"));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
