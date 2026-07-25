package com.bookstore.service.impl;

import com.bookstore.dto.BookRequestDto;
import com.bookstore.dto.BookResponseDto;
import com.bookstore.entity.Book;
import com.bookstore.exception.DuplicateResourceException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookServiceImpl bookService;

    private BookRequestDto sampleRequest() {
        return new BookRequestDto("Clean Code", "978-0132350884", new BigDecimal("39.99"), 10);
    }

    @Test
    void createBook_persistsAndReturnsResponse() {
        BookRequestDto request = sampleRequest();
        when(bookRepository.existsByIsbn(request.isbn())).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            book.setId(1L);
            return book;
        });

        BookResponseDto result = bookService.createBook(request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("Clean Code");
        assertThat(result.isbn()).isEqualTo("978-0132350884");
        assertThat(result.price()).isEqualByComparingTo("39.99");
        assertThat(result.stock()).isEqualTo(10);
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    void createBook_duplicateIsbn_throwsDuplicate() {
        BookRequestDto request = sampleRequest();
        when(bookRepository.existsByIsbn(request.isbn())).thenReturn(true);

        assertThatThrownBy(() -> bookService.createBook(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining(request.isbn());

        verify(bookRepository, never()).save(any());
    }

    @Test
    void getBookById_existing_returnsResponse() {
        Book book = new Book(5L, "DDD", "978-0321125217", new BigDecimal("54.99"), 3);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book));

        BookResponseDto result = bookService.getBookById(5L);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.title()).isEqualTo("DDD");
    }

    @Test
    void getBookById_missing_throwsNotFound() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getBookById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateBook_missing_throwsNotFound() {
        BookRequestDto request = sampleRequest();
        when(bookRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.updateBook(42L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(bookRepository, never()).save(any());
    }

    @Test
    void updateBook_changingToExistingIsbn_throwsDuplicate() {
        Book existing = new Book(1L, "Old Title", "isbn-old", new BigDecimal("10.00"), 1);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        BookRequestDto request = new BookRequestDto("New Title", "isbn-taken", new BigDecimal("12.00"), 2);
        when(bookRepository.existsByIsbn("isbn-taken")).thenReturn(true);

        assertThatThrownBy(() -> bookService.updateBook(1L, request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(bookRepository, never()).save(any());
    }

    @Test
    void deleteBook_missing_throwsNotFound() {
        when(bookRepository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> bookService.deleteBook(7L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(bookRepository, never()).deleteById(any());
    }

    @Test
    void getAllBooks_returnsMappedList() {
        when(bookRepository.findAll()).thenReturn(List.of(
                new Book(1L, "A", "isbn-a", new BigDecimal("10.00"), 1),
                new Book(2L, "B", "isbn-b", new BigDecimal("20.00"), 2)
        ));

        List<BookResponseDto> result = bookService.getAllBooks();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(BookResponseDto::title).containsExactly("A", "B");
    }
}
