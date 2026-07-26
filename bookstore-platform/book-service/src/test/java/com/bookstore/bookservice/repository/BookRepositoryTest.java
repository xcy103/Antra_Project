package com.bookstore.bookservice.repository;

import com.bookstore.bookservice.entity.Author;
import com.bookstore.bookservice.entity.Book;
import com.bookstore.bookservice.support.AbstractPostgresIT;
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
 * Repository slice tests against a real PostgreSQL (Testcontainers): data lands,
 * constraints hold, optimistic locking rejects a stale write.
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
        assertThat(found.get().getAuthor().getName()).isEqualTo("Test Author");
        assertThat(found.get().getVersion()).isEqualTo(0L);
    }

    @Test
    void duplicateIsbn_violatesUniqueConstraint() {
        Author author = persistAuthor();
        bookRepository.saveAndFlush(newBook("isbn-dup", 5, author));

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

        entityManager.createNativeQuery("UPDATE book SET stock = 99, version = version + 1 WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();

        loaded.setStock(7);
        assertThatThrownBy(() -> bookRepository.saveAndFlush(loaded))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
