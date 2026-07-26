package com.bookstore.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Order microservice. Scans {@code com.bookstore} for the shared common beans and
 * enables Feign clients for the call to book-service.
 */
@SpringBootApplication(scanBasePackages = "com.bookstore")
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
