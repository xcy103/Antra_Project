package com.bookstore.bookservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Book catalog microservice. Scans {@code com.bookstore} so the shared beans in
 * the common module (JWT filter, error handling, logging aspect) are picked up.
 */
@SpringBootApplication(scanBasePackages = "com.bookstore")
public class BookServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookServiceApplication.class, args);
    }
}
