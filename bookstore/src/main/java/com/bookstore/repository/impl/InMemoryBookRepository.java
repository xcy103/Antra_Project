package com.bookstore.repository.impl;

import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory {@link BookRepository} for Phase 1 — lets the whole stack run without
 * a database. Phase 2 replaces it with a JPA + PostgreSQL implementation.
 */
@Repository
public class InMemoryBookRepository implements BookRepository {

    private final Map<Long, Book> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public List<Book> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(Book::getId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Book> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Book save(Book book) {
        if (book.getId() == null) {
            book.setId(sequence.incrementAndGet());
        }
        store.put(book.getId(), book);
        return book;
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    @Override
    public boolean existsByIsbn(String isbn) {
        return store.values().stream().anyMatch(book -> book.getIsbn().equals(isbn));
    }
}
