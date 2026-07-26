package com.bookstore.userservice.repository;

import com.bookstore.common.security.Role;
import com.bookstore.userservice.entity.User;
import com.bookstore.userservice.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends AbstractPostgresIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsername_returnsSavedUser() {
        userRepository.save(new User("alice", "alice@example.com", "bcrypt-hash", Role.USER));

        Optional<User> found = userRepository.findByUsername("alice");

        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void existsChecks_reflectPersistedUser() {
        userRepository.save(new User("bob", "bob@example.com", "bcrypt-hash", Role.USER));

        assertThat(userRepository.existsByUsername("bob")).isTrue();
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
        assertThat(userRepository.existsByUsername("carol")).isFalse();
    }
}
