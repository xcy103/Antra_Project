package com.bookstore.repository.impl;

import com.bookstore.entity.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryBookRepositoryTest {

    private InMemoryBookRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBookRepository();
    }

    private Book newBook(String isbn) {
        return new Book(null, "Title " + isbn, isbn, new BigDecimal("19.99"), 5);
    }

    @Test
    void save_assignsIncrementingIds() {
        Book first = repository.save(newBook("isbn-1"));
        Book second = repository.save(newBook("isbn-2"));

        assertThat(first.getId()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(2L);
    }

    @Test
    void findById_returnsSavedBook() {
        Book saved = repository.save(newBook("isbn-1"));

        Optional<Book> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getIsbn()).isEqualTo("isbn-1");
    }

    @Test
    void existsByIsbn_reflectsStoredBooks() {
        repository.save(newBook("isbn-1"));

        assertThat(repository.existsByIsbn("isbn-1")).isTrue();
        assertThat(repository.existsByIsbn("isbn-x")).isFalse();
    }

    @Test
    void deleteById_removesBook() {
        Book saved = repository.save(newBook("isbn-1"));

        repository.deleteById(saved.getId());

        assertThat(repository.existsById(saved.getId())).isFalse();
    }

    @Test
    void findAll_returnsBooksSortedById() {
        repository.save(newBook("isbn-1"));
        repository.save(newBook("isbn-2"));

        assertThat(repository.findAll()).extracting(Book::getId).containsExactly(1L, 2L);
    }
}
