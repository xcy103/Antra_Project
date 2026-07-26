package com.bookstore.bookservice.service;

import com.bookstore.bookservice.dto.BookRequestDto;
import com.bookstore.bookservice.dto.BookResponseDto;

import java.util.List;

/**
 * Book use-cases. Accepts and returns DTOs only.
 */
public interface BookService {

    List<BookResponseDto> getAllBooks();

    BookResponseDto getBookById(Long id);

    BookResponseDto createBook(BookRequestDto request);

    BookResponseDto updateBook(Long id, BookRequestDto request);

    void deleteBook(Long id);
}
