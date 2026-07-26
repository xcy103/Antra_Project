package com.bookstore.bookservice.repository;

import com.bookstore.bookservice.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for authors.
 */
public interface AuthorRepository extends JpaRepository<Author, Long> {
}
