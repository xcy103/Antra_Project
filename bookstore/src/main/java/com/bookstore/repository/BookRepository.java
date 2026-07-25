package com.bookstore.repository;

import com.bookstore.entity.Book;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for books.
 *
 * <p>The method set intentionally mirrors Spring Data's {@code JpaRepository} so
 * that Phase 2 can simply make this interface {@code extends JpaRepository<Book, Long>}
 * (adding {@code existsByIsbn} as a derived query) without touching the service layer.
 */
public interface BookRepository {

    List<Book> findAll();

    Optional<Book> findById(Long id);

    Book save(Book book);

    void deleteById(Long id);

    boolean existsById(Long id);

    boolean existsByIsbn(String isbn);
}
