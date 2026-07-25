package com.bookstore.repository;

import com.bookstore.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for books. The fetch-join queries load the author in
 * the same round-trip so mapping to a DTO with {@code open-in-view=false} never
 * triggers a lazy load (and listing never causes an N+1 on the author).
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    @Query("select b from Book b join fetch b.author")
    List<Book> findAllWithAuthor();

    @Query("select b from Book b join fetch b.author where b.id = :id")
    Optional<Book> findByIdWithAuthor(Long id);
}
