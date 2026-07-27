package com.bookstore.bookservice.controller;

import com.bookstore.bookservice.dto.BookRequestDto;
import com.bookstore.bookservice.dto.BookResponseDto;
import com.bookstore.bookservice.service.BookService;
import com.bookstore.bookservice.service.BrowsingHistoryService;
import com.bookstore.bookservice.service.CoverService;
import com.bookstore.common.exception.DuplicateResourceException;
import com.bookstore.common.exception.ResourceNotFoundException;
import com.bookstore.common.security.JwtUtil;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for book endpoints (filters off; authorization is covered in
 * the security integration test). JwtUtil is mocked to satisfy the common
 * JwtAuthenticationFilter bean that the slice still instantiates.
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

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private BrowsingHistoryService browsingHistoryService;

    @MockitoBean
    private CoverService coverService;

    private BookResponseDto sampleResponse() {
        return new BookResponseDto(1L, "Clean Code", "978-0132350884",
                new BigDecimal("39.99"), 10, 1L, "Robert C. Martin");
    }

    @Test
    void getAllBooks_returnsList() throws Exception {
        when(bookService.getAllBooks()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Clean Code"));
    }

    @Test
    void getBookById_notFound_returns404() throws Exception {
        when(bookService.getBookById(99L))
                .thenThrow(new ResourceNotFoundException("Book not found with id 99"));

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createBook_valid_returns201WithLocation() throws Exception {
        when(bookService.createBook(any())).thenReturn(sampleResponse());
        String body = objectMapper.writeValueAsString(
                new BookRequestDto("Clean Code", "978-0132350884", new BigDecimal("39.99"), 10, 1L));

        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/books/1"));
    }

    @Test
    void createBook_invalidPayload_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new BookRequestDto("", "x", new BigDecimal("-1"), -5, 1L));

        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void createBook_duplicateIsbn_returns409() throws Exception {
        when(bookService.createBook(any()))
                .thenThrow(new DuplicateResourceException("Book already exists with isbn 978-0132350884"));
        String body = objectMapper.writeValueAsString(
                new BookRequestDto("Clean Code", "978-0132350884", new BigDecimal("39.99"), 10, 1L));

        mockMvc.perform(post("/api/books").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }
}
