package com.bookstore.service.impl;

import com.bookstore.dto.BookRequestDto;
import com.bookstore.dto.BookResponseDto;
import com.bookstore.entity.Book;
import com.bookstore.exception.DuplicateResourceException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.BookRepository;
import com.bookstore.service.BookService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for books: DTO/entity mapping, ISBN-uniqueness enforcement, and
 * not-found handling. Writes are single-repository operations in Phase 1; Phase 2
 * makes the multi-step ones {@code @Transactional} once JPA is in place.
 */
@Service
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;

    public BookServiceImpl(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public List<BookResponseDto> getAllBooks() {
        return bookRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public BookResponseDto getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + id));
        return toResponse(book);
    }

    @Override
    public BookResponseDto createBook(BookRequestDto request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateResourceException("Book already exists with isbn " + request.isbn());
        }
        Book book = new Book(null, request.title(), request.isbn(), request.price(), request.stock());
        return toResponse(bookRepository.save(book));
    }

    @Override
    public BookResponseDto updateBook(Long id, BookRequestDto request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + id));

        boolean isbnChanged = !book.getIsbn().equals(request.isbn());
        if (isbnChanged && bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateResourceException("Book already exists with isbn " + request.isbn());
        }

        book.setTitle(request.title());
        book.setIsbn(request.isbn());
        book.setPrice(request.price());
        book.setStock(request.stock());
        return toResponse(bookRepository.save(book));
    }

    @Override
    public void deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book not found with id " + id);
        }
        bookRepository.deleteById(id);
    }

    private BookResponseDto toResponse(Book book) {
        return new BookResponseDto(
                book.getId(), book.getTitle(), book.getIsbn(), book.getPrice(), book.getStock());
    }
}
