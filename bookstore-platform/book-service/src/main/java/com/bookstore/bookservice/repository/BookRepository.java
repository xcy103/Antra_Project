package com.bookstore.bookservice.repository;

import com.bookstore.bookservice.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for books. Fetch-join queries load the author in one
 * round-trip (avoids N+1 and lazy-loading issues with {@code open-in-view=false}).
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    @Query("select b from Book b join fetch b.author")
    List<Book> findAllWithAuthor();

    @Query("select b from Book b join fetch b.author where b.id = :id")
    Optional<Book> findByIdWithAuthor(Long id);
}
