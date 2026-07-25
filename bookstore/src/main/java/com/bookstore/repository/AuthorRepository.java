package com.bookstore.repository;

import com.bookstore.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data JPA repository for authors.
 *
 * <p>{@link #findAll()} (inherited) is the naive query used to demonstrate the
 * N+1 problem: iterating authors and touching {@code author.getBooks()} fires one
 * extra SELECT per author. {@link #findAllWithBooks()} is the fetch-join fix that
 * loads everything in a single query — see docs/02-DESIGN.md.
 */
public interface AuthorRepository extends JpaRepository<Author, Long> {

    @Query("select distinct a from Author a left join fetch a.books")
    List<Author> findAllWithBooks();
}
