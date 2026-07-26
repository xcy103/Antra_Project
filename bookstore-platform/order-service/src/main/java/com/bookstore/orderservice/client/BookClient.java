package com.bookstore.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for book-service. Wrapped by a circuit breaker
 * ({@code feign.circuitbreaker.enabled=true}); when book-service is unavailable
 * the {@link BookClientFallback} kicks in so order placement degrades gracefully
 * instead of hanging.
 */
@FeignClient(name = "book-service", url = "${book-service.url}", fallbackFactory = BookClientFallback.class)
public interface BookClient {

    @GetMapping("/api/books/{id}")
    BookDto getBook(@PathVariable("id") Long id);
}
