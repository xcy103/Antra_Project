package com.bookstore.repository;

import com.bookstore.entity.Role;
import com.bookstore.entity.User;
import com.bookstore.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice tests for users against a real PostgreSQL (Testcontainers).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends AbstractPostgresIT {

    @Autowired
    private UserRepository userRepository;

    private User newUser(String username) {
        return new User(username, username + "@example.com", "bcrypt-hash", Role.USER);
    }

    @Test
    void findByUsername_returnsSavedUser() {
        userRepository.save(newUser("alice"));

        Optional<User> found = userRepository.findByUsername("alice");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void findByUsername_missing_returnsEmpty() {
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }

    @Test
    void existsByUsernameAndEmail_reflectPersistedUser() {
        userRepository.save(newUser("bob"));

        assertThat(userRepository.existsByUsername("bob")).isTrue();
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
        assertThat(userRepository.existsByUsername("carol")).isFalse();
        assertThat(userRepository.existsByEmail("carol@example.com")).isFalse();
    }
}
