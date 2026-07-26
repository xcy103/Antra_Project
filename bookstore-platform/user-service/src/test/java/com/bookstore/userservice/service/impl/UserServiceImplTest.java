package com.bookstore.userservice.service.impl;

import com.bookstore.common.exception.ResourceNotFoundException;
import com.bookstore.common.security.Role;
import com.bookstore.userservice.dto.UserResponse;
import com.bookstore.userservice.entity.User;
import com.bookstore.userservice.repository.UserRepository;
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
    }

    @Test
    void getByUsername_missing_throwsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByUsername("ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
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

        assertThat(result).extracting(UserResponse::username).containsExactly("alice", "admin");
    }
}
