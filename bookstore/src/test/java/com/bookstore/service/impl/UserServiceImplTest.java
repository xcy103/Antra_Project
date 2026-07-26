package com.bookstore.service.impl;

import com.bookstore.dto.UserResponse;
import com.bookstore.entity.Role;
import com.bookstore.entity.User;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User user(Long id, String username, Role role) {
        User u = new User(username, username + "@example.com", "bcrypt-hash", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void getByUsername_found_returnsResponse() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice", Role.USER)));

        UserResponse result = userService.getByUsername("alice");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.role()).isEqualTo(Role.USER);
    }

    @Test
    void getByUsername_missing_throwsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByUsername("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void getById_found_returnsResponse() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "bob", Role.ADMIN)));

        UserResponse result = userService.getById(5L);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void getById_missing_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllUsers_mapsList() {
        when(userRepository.findAll()).thenReturn(List.of(
                user(1L, "alice", Role.USER),
                user(2L, "admin", Role.ADMIN)));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserResponse::username).containsExactly("alice", "admin");
    }
}
