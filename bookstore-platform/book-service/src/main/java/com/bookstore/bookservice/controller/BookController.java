package com.bookstore.bookservice.controller;

import com.bookstore.bookservice.dto.BookRequestDto;
import com.bookstore.bookservice.dto.BookResponseDto;
import com.bookstore.bookservice.dto.BrowsingHistoryEntry;
import com.bookstore.bookservice.service.BookService;
import com.bookstore.bookservice.service.BrowsingHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Book CRUD endpoints. Reads are public; writes require ADMIN (enforced in
 * SecurityConfig). {@code GET /api/books/{id}} is what order-service calls over
 * Feign to snapshot price/stock.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;
    private final BrowsingHistoryService browsingHistoryService;

    public BookController(BookService bookService, BrowsingHistoryService browsingHistoryService) {
        this.bookService = bookService;
        this.browsingHistoryService = browsingHistoryService;
    }

    @GetMapping
    public List<BookResponseDto> getAllBooks() {
        return bookService.getAllBooks();
    }

    /** Recently-viewed books for the current user (reverse chronological). */
    @GetMapping("/me/history")
    public List<BrowsingHistoryEntry> myHistory(Authentication authentication) {
        return browsingHistoryService.getRecentlyViewed(authentication.getName(), 20);
    }

    @GetMapping("/{id}")
    public BookResponseDto getBookById(@PathVariable Long id, Authentication authentication) {
        BookResponseDto book = bookService.getBookById(id);
        // Anonymous reads are allowed (public endpoint); only record for logged-in users.
        if (authentication != null) {
            browsingHistoryService.recordView(authentication.getName(), book.id(), book.title());
        }
        return book;
    }

    @PostMapping
    public ResponseEntity<BookResponseDto> createBook(@Valid @RequestBody BookRequestDto request) {
        BookResponseDto created = bookService.createBook(request);
        return ResponseEntity.created(URI.create("/api/books/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public BookResponseDto updateBook(@PathVariable Long id,
                                      @Valid @RequestBody BookRequestDto request) {
        return bookService.updateBook(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
    }
}
