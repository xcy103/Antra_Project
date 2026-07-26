package com.bookstore.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * User/authentication microservice. Scans {@code com.bookstore} to pick up the
 * shared beans from the common module.
 */
@SpringBootApplication(scanBasePackages = "com.bookstore")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
