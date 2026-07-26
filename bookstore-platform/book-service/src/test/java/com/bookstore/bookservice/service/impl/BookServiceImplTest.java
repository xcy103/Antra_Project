package com.bookstore.bookservice.service.impl;

import com.bookstore.bookservice.dto.BookRequestDto;
import com.bookstore.bookservice.dto.BookResponseDto;
import com.bookstore.bookservice.entity.Author;
import com.bookstore.bookservice.entity.Book;
import com.bookstore.bookservice.repository.AuthorRepository;
import com.bookstore.bookservice.repository.BookRepository;
import com.bookstore.common.exception.DuplicateResourceException;
import com.bookstore.common.exception.ResourceNotFoundException;
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

    @Mock
    private AuthorRepository authorRepository;

    @InjectMocks
    private BookServiceImpl bookService;

    private final Author author = new Author(1L, "Robert C. Martin");

    private BookRequestDto sampleRequest() {
        return new BookRequestDto("Clean Code", "978-0132350884", new BigDecimal("39.99"), 10, 1L);
    }

    private Book sampleBook(Long id) {
        Book book = new Book("Clean Code", "978-0132350884", new BigDecimal("39.99"), 10, author);
        book.setId(id);
        return book;
    }

    @Test
    void createBook_persistsAndReturnsResponse() {
        BookRequestDto request = sampleRequest();
        when(bookRepository.existsByIsbn(request.isbn())).thenReturn(false);
        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            book.setId(1L);
            return book;
        });

        BookResponseDto result = bookService.createBook(request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.authorName()).isEqualTo("Robert C. Martin");
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
    void createBook_missingAuthor_throwsNotFound() {
        BookRequestDto request = sampleRequest();
        when(bookRepository.existsByIsbn(request.isbn())).thenReturn(false);
        when(authorRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.createBook(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Author");

        verify(bookRepository, never()).save(any());
    }

    @Test
    void getBookById_missing_throwsNotFound() {
        when(bookRepository.findByIdWithAuthor(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getBookById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
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
        when(bookRepository.findAllWithAuthor()).thenReturn(List.of(sampleBook(1L), sampleBook(2L)));

        List<BookResponseDto> result = bookService.getAllBooks();

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(dto -> assertThat(dto.authorName()).isEqualTo("Robert C. Martin"));
    }
}
