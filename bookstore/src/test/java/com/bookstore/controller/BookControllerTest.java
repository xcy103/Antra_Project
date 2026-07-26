package com.bookstore.controller;

import com.bookstore.dto.BookRequestDto;
import com.bookstore.dto.BookResponseDto;
import com.bookstore.exception.DuplicateResourceException;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.security.JwtUtil;
import com.bookstore.service.BookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the book endpoints: request mapping, validation, status
 * codes and error JSON. Security filters are disabled here (authorization is
 * covered end-to-end in SecurityIntegrationTest); the service is mocked.
 */
@WebMvcTest(BookController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookService bookService;

    // The JwtAuthenticationFilter bean is created by the web slice even with
    // filters disabled, so its JwtUtil dependency must be satisfied.
    @MockitoBean
    private JwtUtil jwtUtil;

    private BookResponseDto sampleResponse() {
        return new BookResponseDto(1L, "Clean Code", "978-0132350884",
                new BigDecimal("39.99"), 10, 1L, "Robert C. Martin");
    }

    @Test
    void getAllBooks_returnsList() throws Exception {
        when(bookService.getAllBooks()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Clean Code"))
                .andExpect(jsonPath("$[0].authorName").value("Robert C. Martin"));
    }

    @Test
    void getBookById_returnsBook() throws Exception {
        when(bookService.getBookById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isbn").value("978-0132350884"));
    }

    @Test
    void getBookById_notFound_returns404() throws Exception {
        when(bookService.getBookById(99L))
                .thenThrow(new ResourceNotFoundException("Book not found with id 99"));

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Book not found with id 99"));
    }

    @Test
    void createBook_valid_returns201WithLocation() throws Exception {
        when(bookService.createBook(any())).thenReturn(sampleResponse());
        String body = objectMapper.writeValueAsString(
                new BookRequestDto("Clean Code", "978-0132350884", new BigDecimal("39.99"), 10, 1L));

        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/books/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createBook_invalidPayload_returns400WithFieldErrors() throws Exception {
        String body = objectMapper.writeValueAsString(
                new BookRequestDto("", "978-0132350884", new BigDecimal("-1"), -5, 1L));

        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists())
                .andExpect(jsonPath("$.fieldErrors.stock").exists());
    }

    @Test
    void createBook_duplicateIsbn_returns409() throws Exception {
        when(bookService.createBook(any()))
                .thenThrow(new DuplicateResourceException("Book already exists with isbn 978-0132350884"));
        String body = objectMapper.writeValueAsString(
                new BookRequestDto("Clean Code", "978-0132350884", new BigDecimal("39.99"), 10, 1L));

        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void deleteBook_returns204() throws Exception {
        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteBook_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Book not found with id 99"))
                .when(bookService).deleteBook(99L);

        mockMvc.perform(delete("/api/books/99"))
                .andExpect(status().isNotFound());
    }
}
