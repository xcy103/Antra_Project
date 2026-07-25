package com.bookstore.repository;

import com.bookstore.entity.Author;
import com.bookstore.entity.Book;
import com.bookstore.support.AbstractPostgresIT;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository slice tests against a real PostgreSQL (Testcontainers). Verifies that
 * data actually lands in the DB, that the DB-level constraints hold, and that
 * optimistic locking rejects a stale write.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookRepositoryTest extends AbstractPostgresIT {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private EntityManager entityManager;

    private Author persistAuthor() {
        return authorRepository.save(new Author("Test Author"));
    }

    private Book newBook(String isbn, int stock, Author author) {
        return new Book("Some Title", isbn, new BigDecimal("19.99"), stock, author);
    }

    @Test
    void savesAndReadsBackWithAuthor() {
        Author author = persistAuthor();
        Book saved = bookRepository.save(newBook("isbn-100", 5, author));
        entityManager.flush();
        entityManager.clear();

        Optional<Book> found = bookRepository.findByIdWithAuthor(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getIsbn()).isEqualTo("isbn-100");
        assertThat(found.get().getAuthor().getName()).isEqualTo("Test Author");
        assertThat(found.get().getVersion()).isEqualTo(0L);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void existsByIsbn_reflectsPersistedBooks() {
        Author author = persistAuthor();
        bookRepository.save(newBook("isbn-200", 5, author));
        entityManager.flush();

        assertThat(bookRepository.existsByIsbn("isbn-200")).isTrue();
        assertThat(bookRepository.existsByIsbn("isbn-missing")).isFalse();
    }

    @Test
    void duplicateIsbn_violatesUniqueConstraint() {
        Author author = persistAuthor();
        bookRepository.saveAndFlush(newBook("isbn-dup", 5, author));

        // IDENTITY id generation makes save() insert immediately, so the unique
        // violation surfaces on the second write, not on a later flush.
        assertThatThrownBy(() -> bookRepository.saveAndFlush(newBook("isbn-dup", 3, author)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void negativeStock_violatesCheckConstraint() {
        Author author = persistAuthor();

        assertThatThrownBy(() -> bookRepository.saveAndFlush(newBook("isbn-neg", -1, author)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void staleUpdate_failsOptimisticLock() {
        Author author = persistAuthor();
        Book saved = bookRepository.save(newBook("isbn-lock", 5, author));
        entityManager.flush();
        entityManager.clear();

        Long id = saved.getId();
        Book loaded = bookRepository.findById(id).orElseThrow();

        // Simulate a concurrent transaction bumping the version behind our back.
        entityManager.createNativeQuery("UPDATE book SET stock = 99, version = version + 1 WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();

        // Our in-memory copy still has the old version -> flush must fail.
        loaded.setStock(7);
        assertThatThrownBy(() -> {
            bookRepository.saveAndFlush(loaded);
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
