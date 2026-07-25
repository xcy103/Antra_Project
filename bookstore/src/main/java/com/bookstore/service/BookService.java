package com.bookstore.service;

import com.bookstore.dto.BookRequestDto;
import com.bookstore.dto.BookResponseDto;

import java.util.List;

/**
 * Book use-cases. Accepts and returns DTOs only, keeping entities out of the
 * controller layer.
 */
public interface BookService {

    List<BookResponseDto> getAllBooks();

    BookResponseDto getBookById(Long id);

    BookResponseDto createBook(BookRequestDto request);

    BookResponseDto updateBook(Long id, BookRequestDto request);

    void deleteBook(Long id);
}
