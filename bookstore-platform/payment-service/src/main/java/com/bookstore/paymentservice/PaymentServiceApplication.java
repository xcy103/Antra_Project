package com.bookstore.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Payment microservice. Scans {@code com.bookstore} for the shared common beans.
 */
@SpringBootApplication(scanBasePackages = "com.bookstore")
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
