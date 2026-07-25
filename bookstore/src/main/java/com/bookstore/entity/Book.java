package com.bookstore.entity;

import java.math.BigDecimal;

/**
 * Book domain entity.
 *
 * <p>Phase 1: a plain in-memory object (no persistence annotations). Phase 2 turns
 * this into a JPA {@code @Entity}, adds the {@code @ManyToOne} relation to Author,
 * an optimistic-lock {@code @Version} field, and a Flyway-managed table.
 */
public class Book {

    private Long id;
    private String title;
    private String isbn;
    private BigDecimal price;
    private Integer stock;

    public Book() {
    }

    public Book(Long id, String title, String isbn, BigDecimal price, Integer stock) {
        this.id = id;
        this.title = title;
        this.isbn = isbn;
        this.price = price;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}
