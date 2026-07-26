package com.bookstore.bookservice.service.impl;

import com.bookstore.bookservice.dto.BookRequestDto;
import com.bookstore.bookservice.dto.BookResponseDto;
import com.bookstore.bookservice.entity.Author;
import com.bookstore.bookservice.entity.Book;
import com.bookstore.bookservice.repository.AuthorRepository;
import com.bookstore.bookservice.repository.BookRepository;
import com.bookstore.bookservice.service.BookService;
import com.bookstore.common.exception.DuplicateResourceException;
import com.bookstore.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for books: DTO/entity mapping, ISBN uniqueness, author resolution.
 */
@Service
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;

    public BookServiceImpl(BookRepository bookRepository, AuthorRepository authorRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookResponseDto> getAllBooks() {
        return bookRepository.findAllWithAuthor().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponseDto getBookById(Long id) {
        Book book = bookRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + id));
        return toResponse(book);
    }

    @Override
    @Transactional
    public BookResponseDto createBook(BookRequestDto request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateResourceException("Book already exists with isbn " + request.isbn());
        }
        Author author = findAuthor(request.authorId());
        Book book = new Book(request.title(), request.isbn(), request.price(), request.stock(), author);
        return toResponse(bookRepository.save(book));
    }

    @Override
    @Transactional
    public BookResponseDto updateBook(Long id, BookRequestDto request) {
        Book book = bookRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + id));

        boolean isbnChanged = !book.getIsbn().equals(request.isbn());
        if (isbnChanged && bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateResourceException("Book already exists with isbn " + request.isbn());
        }
        if (!book.getAuthor().getId().equals(request.authorId())) {
            book.setAuthor(findAuthor(request.authorId()));
        }

        book.setTitle(request.title());
        book.setIsbn(request.isbn());
        book.setPrice(request.price());
        book.setStock(request.stock());
        return toResponse(bookRepository.save(book));
    }

    @Override
    @Transactional
    public void deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book not found with id " + id);
        }
        bookRepository.deleteById(id);
    }

    private Author findAuthor(Long authorId) {
        return authorRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("Author not found with id " + authorId));
    }

    private BookResponseDto toResponse(Book book) {
        return new BookResponseDto(
                book.getId(),
                book.getTitle(),
                book.getIsbn(),
                book.getPrice(),
                book.getStock(),
                book.getAuthor().getId(),
                book.getAuthor().getName());
    }
}
